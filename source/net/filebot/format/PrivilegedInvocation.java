
package net.filebot.format;


import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;


public final class PrivilegedInvocation implements InvocationHandler {

	private final Object object;
	private final AccessControlContext context;


	private PrivilegedInvocation(Object object, AccessControlContext context) {
		this.object = object;
		this.context = context;
	}


	@Override
	public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {
		try {
			return AccessController.doPrivileged(new PrivilegedExceptionAction<Object>() {

				@Override
				public Object run() throws Exception {
					return method.invoke(object, args);
				}
			}, context);
		} catch (PrivilegedActionException e) {
			Throwable cause = e.getException();

			// the underlying method may have throw an exception
			if (cause instanceof InvocationTargetException) {
				// get actual cause
				cause = cause.getCause();
			}

			// forward cause
			throw cause;
		}
	}


	public static <I> I newProxy(Class<I> interfaceClass, I object, AccessControlContext context) {
		InvocationHandler invocationHandler = new PrivilegedInvocation(object, context);
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

		// create dynamic invocation proxy
		return interfaceClass.cast(Proxy.newProxyInstance(classLoader, new Class[] { interfaceClass }, invocationHandler));
	}
}
