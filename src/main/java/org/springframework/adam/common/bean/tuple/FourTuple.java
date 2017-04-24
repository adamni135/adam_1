/**
 * 
 */
package org.springframework.adam.common.bean.tuple;

/**
 * @author USER
 *
 */
public class FourTuple<A, B, C, D> extends ThreeTuple<A, B, C> {

	protected D d;

	public FourTuple() {
		super();
	}

	public FourTuple(A a, B b, C c, D d) {
		super(a, b, c);
		this.d = d;
	}

	public D getD() {
		return d;
	}

	public void setD(D d) {
		this.d = d;
	}

}
