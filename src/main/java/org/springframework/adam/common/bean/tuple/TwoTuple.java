/**
 * 
 */
package org.springframework.adam.common.bean.tuple;

/**
 * @author USER
 *
 */
public class TwoTuple<A, B> extends OneTuple<A> {

	protected B b;

	public TwoTuple() {
		super();
	}

	public TwoTuple(A a, B b) {
		super(a);
		this.b = b;
	}

	public B getB() {
		return b;
	}

	public void setB(B b) {
		this.b = b;
	}
}
