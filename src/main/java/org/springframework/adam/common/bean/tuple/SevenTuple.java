/**
 * 
 */
package org.springframework.adam.common.bean.tuple;

/**
 * @author USER
 *
 */
public class SevenTuple<A, B, C, D, E, F, G> extends SixTuple<A, B, C, D, E, F> {

	protected G g;

	public SevenTuple() {
		super();
	}

	public SevenTuple(A a, B b, C c, D d, E e, F f, G g) {
		super(a, b, c, d, e, f);
		this.g = g;
	}

	public G getG() {
		return g;
	}

	public void setG(G g) {
		this.g = g;
	}

}
