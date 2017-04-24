/**
 * 
 */
package org.springframework.adam.common.bean.tuple;

/**
 * @author USER
 *
 */
public class ThreeTuple<A, B, C> extends TwoTuple<A, B> {

	protected C c;

	public ThreeTuple() {
		super();
	}

	public ThreeTuple(A a, B b, C c) {
		super(a, b);
		this.c = c;
	}

	public C getC() {
		return c;
	}

	public void setC(C c) {
		this.c = c;
	}

}
