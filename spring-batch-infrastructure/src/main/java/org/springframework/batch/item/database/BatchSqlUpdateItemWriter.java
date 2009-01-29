/*
 * Copyright 2006-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.batch.item.database;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Set;

import org.springframework.batch.item.ClearFailedException;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatContext;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.util.Assert;

/**
 * {@link ItemWriter} that uses the batching features from
 * {@link PreparedStatement} if available and can take some rudimentary steps to
 * locate a failure during a flush, and identify the items that failed. When one
 * of those items is encountered again the batch is flushed aggressively so that
 * the bad item is eventually identified and can be dealt with in isolation.<br/>
 * 
 * The user must provide an SQL query and a special callback
 * {@link ItemPreparedStatementSetter}, which is responsible for mapping the
 * item to a PreparedStatement.<br/>
 * 
 * It is expected that {@link #write(Object)} is called inside a transaction,
 * and that {@link #flush()} is then subsequently called before the transaction
 * commits, or {@link #clear()} before it rolls back.<br/>
 * 
 * The writer is thread safe after its properties are set (normal singleton
 * behaviour), so it can be used to write in multiple concurrent transactions.
 * Note, however, that the set of failed items is stored in a collection
 * internally, and this collection is never cleared, so it is not a great idea
 * to go on using the writer indefinitely. Normally it would be used for the
 * duration of a batch job and then discarded.
 * 
 * @author Dave Syer
 * 
 */
public class BatchSqlUpdateItemWriter extends AbstractTransactionalResourceItemWriter implements InitializingBean {

	/**
	 * Key for items processed in the current transaction {@link RepeatContext}.
	 */
	private static final String ITEMS_PROCESSED = BatchSqlUpdateItemWriter.class.getName() + ".ITEMS_PROCESSED";

	private JdbcOperations jdbcTemplate;

	private ItemPreparedStatementSetter preparedStatementSetter;

	private String sql;

	/**
	 * Public setter for the query string to execute on write. The parameters
	 * should correspond to those known to the
	 * {@link ItemPreparedStatementSetter}.
	 * @param sql the query to set
	 */
	public void setSql(String sql) {
		this.sql = sql;
	}

	/**
	 * Public setter for the {@link ItemPreparedStatementSetter}.
	 * @param preparedStatementSetter the {@link ItemPreparedStatementSetter} to
	 * set
	 */
	public void setItemPreparedStatementSetter(ItemPreparedStatementSetter preparedStatementSetter) {
		this.preparedStatementSetter = preparedStatementSetter;
	}

	/**
	 * Public setter for the {@link JdbcOperations}.
	 * @param jdbcTemplate the {@link JdbcOperations} to set
	 */
	public void setJdbcTemplate(JdbcOperations jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/**
	 * Check mandatory properties - there must be a delegate.
	 */
	public void afterPropertiesSet() throws Exception {
		Assert.notNull(jdbcTemplate, "BatchSqlUpdateItemWriter requires an data source.");
		Assert.notNull(preparedStatementSetter, "BatchSqlUpdateItemWriter requires a ItemPreparedStatementSetter");
	}

	/**
	 * Create and execute batch prepared statement.
	 */
	protected void doFlush() {
		final Set processed = getProcessed();
		if (!processed.isEmpty()) {
			jdbcTemplate.execute(sql, new PreparedStatementCallback() {
				public Object doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
					for (Iterator iterator = processed.iterator(); iterator.hasNext();) {
						Object item = (Object) iterator.next();
						preparedStatementSetter.setValues(item, ps);
						ps.addBatch();
					}
					return ps.executeBatch();
				}
			});
		}
	}

	protected String getResourceKey() {
		return ITEMS_PROCESSED;
	}

	/**
	 * No-op.
	 */
	protected void doWrite(Object output) {
	}
	
	/**
	 * No-op.
	 */
	protected void doClear() throws ClearFailedException {
	}

}