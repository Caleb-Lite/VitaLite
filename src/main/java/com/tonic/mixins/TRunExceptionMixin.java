package com.tonic.mixins;

import com.tonic.Logger;
import com.tonic.injector.annotations.Disable;
import com.tonic.injector.annotations.Mixin;
import com.tonic.injector.util.ExceptionUtil;

@Mixin("Client")
public class TRunExceptionMixin {
//    @Disable("RunException_sendStackTrace")
//    public static boolean sendStackTrace(String message, Throwable throwable) {
//        if(throwable != null)
//        {
//            Logger.error(message);
//            Logger.error(ExceptionUtil.formatException(throwable));
//        }
//        return false;
//    }

    @Disable("newRunException")
    public static boolean newRunException(Throwable throwable, String message) {
        if((message != null && message.equals("bj.ac()")) || throwable instanceof NullPointerException)
            return true;

        if(throwable != null)
        {
            Logger.error(message);
            Logger.error(ExceptionUtil.formatException(throwable));
        }
        return false;
    }
}
