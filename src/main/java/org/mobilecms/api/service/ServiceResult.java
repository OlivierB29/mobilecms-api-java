package org.mobilecms.api.service;

public class ServiceResult {

    private final int code;
    private final Object result;
    private final String error;

    public ServiceResult(int code, Object result) {
        this(code, result, null);
    }

    public ServiceResult(int code, Object result, String error) {
        this.code = code;
        this.result = result;
        this.error = error;
    }

    public static ServiceResult ok(Object result) {
        return new ServiceResult(200, result);
    }

    public static ServiceResult error(int code, String message) {
        return new ServiceResult(code, java.util.Map.of("error", message), message);
    }

    public int getCode() {
        return code;
    }

    public Object getResult() {
        return result;
    }

    public String getError() {
        return error;
    }

    public boolean isOk() {
        return code == 200;
    }
}
