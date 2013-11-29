package com.dataiku.wt1;

import java.util.UUID;

public class UUIDGenerator {
	public static String generate() {
		StringBuilder sb = new StringBuilder();
		UUID uuid = UUID.randomUUID();
		sb.append(String.format("%016x%016x", uuid.getMostSignificantBits(), uuid.getLeastSignificantBits()));
		return sb.toString();
	}
	public static String fromSeed(String seed) {
		StringBuilder sb = new StringBuilder();
		UUID uuid = UUID.nameUUIDFromBytes(seed.getBytes());
		sb.append(String.format("%016x%016x", uuid.getMostSignificantBits(), uuid.getLeastSignificantBits()));
		return sb.toString();
	}
}
