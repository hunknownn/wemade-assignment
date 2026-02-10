package com.example.wemadeassignment.parser;

import com.example.wemadeassignment.domain.AccessLog;

import java.io.InputStream;
import java.util.function.Consumer;

/** CSV 접속 로그 스트리밍 파서 */
public interface CsvLogParser {

    /**
     * CSV 입력 스트림을 한 줄씩 파싱하여 콜백으로 전달한다.
     * 전체 로그를 메모리에 보관하지 않고, 파싱 즉시 Consumer로 위임.
     */
    ParseStatistics parse(InputStream inputStream, Consumer<AccessLog> logConsumer);
}
