
package net.filebot.util;


import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;


public class DefaultThreadFactory implements ThreadFactory {

	private final AtomicInteger threadNumber = new AtomicInteger(0);
	private final ThreadGroup group;

	private final int priority;
	private final boolean daemon;


	public DefaultThreadFactory(String name) {
		this(name, Thread.NORM_PRIORITY);
	}


	public DefaultThreadFactory(String name, int priority) {
		this(name, priority, false);
	}


	public DefaultThreadFactory(String groupName, int priority, boolean daemon) {
		SecurityManager sm = System.getSecurityManager();
		ThreadGroup parentGroup = (sm != null) ? sm.getThreadGroup() : Thread.currentThread().getThreadGroup();

		this.group = new ThreadGroup(parentGroup, groupName);

		this.daemon = daemon;
		this.priority = priority;
	}


	@Override
	public Thread newThread(Runnable r) {
		Thread thread = new Thread(group, r, String.format("%s-thread-%d", group.getName(), threadNumber.incrementAndGet()));

		if (daemon != thread.isDaemon())
			thread.setDaemon(daemon);

		if (priority != thread.getPriority())
			thread.setPriority(priority);

		return thread;
	}


	public ThreadGroup getThreadGroup() {
		return group;
	}

}
