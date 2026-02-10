package com.example.wemadeassignment.domain;

public record ParseErrorSample(
        int lineNumber,
        String line,
        String reason
) {
}
