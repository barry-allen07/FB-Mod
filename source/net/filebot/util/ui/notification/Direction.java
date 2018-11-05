
package net.filebot.util.ui.notification;


import javax.swing.SwingConstants;


public enum Direction {
	CENTER(0, 0, 0.5, 0.5, SwingConstants.CENTER),
	NORTH(0, -1, 0.5, 0.0, SwingConstants.NORTH),
	NORTH_EAST(1, -1, 1.0, 0.0, SwingConstants.NORTH_EAST),
	EAST(1, 0, 1.0, 0.5, SwingConstants.EAST),
	SOUTH_EAST(1, 1, 1.0, 1.0, SwingConstants.SOUTH_EAST),
	SOUTH(0, 1, 0.5, 1.0, SwingConstants.SOUTH),
	SOUTH_WEST(-1, 1, 0.0, 1.0, SwingConstants.SOUTH_WEST),
	WEST(-1, 0, 0.0, 0.5, SwingConstants.WEST),
	NORTH_WEST(-1, -1, 0.0, 0.0, SwingConstants.NORTH_WEST);

	public final int vx;
	public final int vy;
	public final double ax;
	public final double ay;
	public final int swingConstant;


	private Direction(int vx, int vy, double ax, double ay, int swingConstant) {
		this.vx = vx;
		this.vy = vy;
		this.ax = ax;
		this.ay = ay;
		this.swingConstant = swingConstant;
	}
}
