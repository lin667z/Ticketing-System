package org.ticketing_system.framework.starter.convention.exception;

import org.ticketing_system.framework.starter.convention.errorcode.BaseErrorCode;
import org.ticketing_system.framework.starter.convention.errorcode.IErrorCode;

/**
 * 远程服务调用异常
 * @author lin667z
 */
public class RemoteException extends AbstractException {

    public RemoteException(String message) {
        this(message, null, BaseErrorCode.REMOTE_ERROR);
    }

    public RemoteException(String message, IErrorCode errorCode) {
        this(message, null, errorCode);
    }

    public RemoteException(String message, Throwable throwable, IErrorCode errorCode) {
        super(message, throwable, errorCode);
    }

    @Override
    public String toString() {
        return "RemoteException{" +
                "code='" + errorCode + "'," +
                "message='" + errorMessage + "'" +
                '}';
    }
}


