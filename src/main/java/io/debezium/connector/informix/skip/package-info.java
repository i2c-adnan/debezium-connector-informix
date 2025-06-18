/**
 * This package contains classes for handling skip configuration in Debezium's Informix connector.
 *
 * The skip configuration allows users to define rules for skipping specific CDC events based on:
 * - Table names
 * - Operation types (INSERT, UPDATE, DELETE, TRUNCATE)
 * - Column conditions with complex logic
 * - Functions and transformations
 *
 * Key components:
 * - SkipOperation: Enum for CDC operation types
 * - Function: Support for string and date functions
 * - Condition: Column-level conditions with operators
 * - ConditionGroup: Nested conditions with logical operators
 * - CaptureSkipConfig: Table-level skip configuration
 * - SkipConfigParser: JSON configuration parser
 * - SkipConfigProcessor: Condition evaluation engine
 *
 * Example configuration:
 * {
 *   "captureSkipConfig": [{
 *     "tableName": "users",
 *     "skipOperations": ["i", "u"],
 *     "conditions": [{
 *       "columnName": "status",
 *       "operator": "=",
 *       "value": "inactive",
 *       "logicalOperator": "AND"
 *     }]
 *   }]
 * }
 */
package io.debezium.connector.informix.skip;
