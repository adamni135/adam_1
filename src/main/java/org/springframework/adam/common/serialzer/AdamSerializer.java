/**
 * 
 */
package org.springframework.adam.common.serialzer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.esotericsoftware.kryo.pool.KryoPool;

/**
 * @author USER
 *
 */
public class AdamSerializer {

	private static AdamSerializer adamKryoPool = new AdamSerializer();

	private KryoPool pool;

	private AdamSerializer() {
		init();
	}

	public static AdamSerializer instance() {
		return adamKryoPool;
	}

	private void init() {
		KryoFactory factory = new AdamSerializeFactory();
		KryoPool pool = new KryoPool.Builder(factory).build();
		this.pool = pool;
	}

	public byte[] serialize(Object obj) throws IOException {
		Kryo kryo = pool.borrow();
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		Output output = new Output(baos);
		kryo.writeObject(output, obj);
		output.flush();
		output.close();
		byte[] b = baos.toByteArray();
		baos.flush();
		baos.close();
		return b;
	}

	public <T> T deserialize(byte[] b, Class<T> clazz) {
		Input input = new Input(b);
		input.close();
		Kryo kryo = pool.borrow();
		return kryo.readObject(input, clazz);
	}
}
