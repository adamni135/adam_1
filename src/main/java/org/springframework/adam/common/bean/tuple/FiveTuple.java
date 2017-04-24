/**
 * 
 */
package org.springframework.adam.common.bean.tuple;

/**
 * @author USER
 *
 */
public class FiveTuple<A, B, C, D, E> extends FourTuple<A, B, C, D> {

	protected E e;

	public FiveTuple() {
		super();
	}

	public FiveTuple(A a, B b, C c, D d, E e) {
		super(a, b, c, d);
		this.e = e;
	}

	public E getE() {
		return e;
	}

	public void setE(E e) {
		this.e = e;
	}

}
