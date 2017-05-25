/**
 * 
 */
package org.springframework.adam.common.utils;

/**
 * @author USER
 *
 */
public class HashUtils {

	public static final int hash(Object key) {
		int h;
		return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
	}

	
	public static void main(String[] args) {
		System.out.println(2 << 16);
	}
}
