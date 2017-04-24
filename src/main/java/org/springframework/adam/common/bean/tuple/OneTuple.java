/**
 * 
 */
package org.springframework.adam.common.bean.tuple;

/**
 * @author USER
 *
 */
public class OneTuple<A> {

	protected A a;

	public OneTuple() {
		super();
	}

	public OneTuple(A a) {
		super();
		this.a = a;
	}

	public A getA() {
		return a;
	}

	public void setA(A a) {
		this.a = a;
	}
}
