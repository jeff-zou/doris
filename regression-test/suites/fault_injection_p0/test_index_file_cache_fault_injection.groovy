// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

suite("test_index_file_cache_fault_injection", "nonConcurrent") {
    def indexTbName = "test_index_file_cache_fault_injection"

    sql "DROP TABLE IF EXISTS ${indexTbName}"
    sql """
      CREATE TABLE ${indexTbName} (
        `@timestamp` int(11) NULL COMMENT "",
        `clientip` varchar(20) NULL COMMENT "",
        `request` text NULL COMMENT "",
        `status` int(11) NULL COMMENT "",
        `size` int(11) NULL COMMENT "",
        INDEX request_idx (`request`) USING INVERTED PROPERTIES("parser" = "english", "support_phrase" = "true") COMMENT '',
      ) ENGINE=OLAP
      DUPLICATE KEY(`@timestamp`)
      COMMENT "OLAP"
      DISTRIBUTED BY RANDOM BUCKETS 1
      PROPERTIES (
        "replication_allocation" = "tag.location.default: 1",
        "disable_auto_compaction" = "true"
      );
    """

    def load_httplogs_data = {table_name, label, read_flag, format_flag, file_name, ignore_failure=false,
                        expected_succ_rows = -1, load_to_single_tablet = 'true' ->

        // load the json data
        streamLoad {
            table "${table_name}"

            // set http request header params
            set 'label', label + "_" + UUID.randomUUID().toString()
            set 'read_json_by_line', read_flag
            set 'format', format_flag
            file file_name // import json file
            time 10000 // limit inflight 10s
            if (expected_succ_rows >= 0) {
                set 'max_filter_ratio', '1'
            }

            // if declared a check callback, the default check condition will ignore.
            // So you must check all condition
            check { result, exception, startTime, endTime ->
		        if (ignore_failure && expected_succ_rows < 0) { return }
                    if (exception != null) {
                        throw exception
                    }
                    log.info("Stream load result: ${result}".toString())
                    def json = parseJson(result)
            }
        }
    }

    try {
        load_httplogs_data.call(indexTbName, 'test_index_file_cache_fault_injection', 'true', 'json', 'documents-1000.json')
        load_httplogs_data.call(indexTbName, 'test_index_file_cache_fault_injection', 'true', 'json', 'documents-1000.json')

        sql "sync"
        sql """ set enable_common_expr_pushdown = true; """

        try {
            GetDebugPoint().enableDebugPointForAllBEs("CSIndexInput.readInternal")
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix '0'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix '1'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix '2'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix '3'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix '4'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix '5'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix '6'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix '7'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix '8'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix '9'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix 'a'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix 'b'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix 'c'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix 'd'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix 'e'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix 'f'; """
            qt_sql """ select count() from ${indexTbName} where request match_phrase_prefix 'g'; """
        } finally {
            GetDebugPoint().disableDebugPointForAllBEs("CSIndexInput.readInternal")
        }
    } finally {
    }
}