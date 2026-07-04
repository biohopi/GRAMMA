package com.example;

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public interface IPackageManager extends IInterface {
    void grantRuntimePermission(String packageName, String permissionName, int userId) throws RemoteException;

    abstract class Stub {
        public static IPackageManager asInterface(final IBinder binder) {
            if (binder == null) {
                return null;
            }
            try {
                // Load the system's real IPackageManager and its Stub
                final Class<?> realStubClass = Class.forName("android.content.pm.IPackageManager$Stub");
                final Method asInterfaceMethod = realStubClass.getMethod("asInterface", IBinder.class);
                final Object realInstance = asInterfaceMethod.invoke(null, binder);

                return (IPackageManager) Proxy.newProxyInstance(
                    IPackageManager.class.getClassLoader(),
                    new Class<?>[]{IPackageManager.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (method.getName().equals("asBinder")) {
                                return binder;
                            }
                            try {
                                // Delegate to the real IPackageManager system service instance
                                Method realMethod = realInstance.getClass().getMethod(method.getName(), method.getParameterTypes());
                                return realMethod.invoke(realInstance, args);
                            } catch (java.lang.reflect.InvocationTargetException e) {
                                throw e.getCause();
                            }
                        }
                    }
                );
            } catch (Throwable t) {
                throw new RuntimeException("Failed to bind IPackageManager stub via reflection", t);
            }
        }
    }
}
