package com.example.wemadeassignment.exception;

public class ServerBusyException extends RuntimeException {

    public ServerBusyException() {
        super("서버가 바쁩니다. 잠시 후 다시 시도해주세요.");
    }
}
