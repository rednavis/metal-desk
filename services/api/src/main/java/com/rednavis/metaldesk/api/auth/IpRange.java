package com.rednavis.metaldesk.api.auth;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An IP address or CIDR block, such as {@code 10.0.0.0/8}, for matching trusted proxies.
 *
 * <p>Only literal addresses are ever parsed, so matching never triggers a DNS lookup on text that
 * came from a request header.
 *
 * @param network the network address bytes, 4 for IPv4 or 16 for IPv6
 * @param prefixBits how many leading bits must match
 */
public record IpRange(byte[] network, int prefixBits) {

  private static final Pattern LITERAL = Pattern.compile("[0-9a-fA-F:.]+");
  private static final int BITS_PER_BYTE = 8;

  /** Copies the address, so the range cannot be changed through the caller's array. */
  public IpRange {
    network = network.clone();
  }

  /**
   * The network address.
   *
   * @return a copy of the address bytes
   */
  @Override
  public byte[] network() {
    return network.clone();
  }

  /**
   * Parses a configured address or CIDR block.
   *
   * @param text an IP literal, optionally followed by {@code /prefix}
   * @return the range
   * @throws IllegalArgumentException if it is not a valid literal or prefix
   */
  public static IpRange parse(String text) {
    final int slash = text.indexOf('/');
    final String address = slash < 0 ? text : text.substring(0, slash);
    final byte[] bytes =
        literal(address)
            .orElseThrow(() -> new IllegalArgumentException("Not an IP address: " + text));
    final int max = bytes.length * BITS_PER_BYTE;
    final int prefix = slash < 0 ? max : Integer.parseInt(text.substring(slash + 1));
    if (prefix < 0 || prefix > max) {
      throw new IllegalArgumentException("Invalid prefix length: " + text);
    }
    return new IpRange(bytes, prefix);
  }

  /**
   * Reads an IP literal without any name lookup.
   *
   * @param text the text to read
   * @return its bytes, or empty if it is not an IP literal
   */
  public static Optional<byte[]> literal(String text) {
    Optional<byte[]> bytes = Optional.empty();
    if (text != null && LITERAL.matcher(text).matches()) {
      try {
        bytes = Optional.of(InetAddress.getByName(text).getAddress());
      } catch (UnknownHostException notLiteral) {
        bytes = Optional.empty();
      }
    }
    return bytes;
  }

  /**
   * Tells whether an address lies in this range.
   *
   * @param address the address bytes
   * @return whether the first {@code prefixBits} bits agree; an address of the other family never
   *     matches
   */
  public boolean contains(byte[] address) {
    return address.length == network.length && matchesPrefix(address);
  }

  private boolean matchesPrefix(byte[] address) {
    final int whole = prefixBits / BITS_PER_BYTE;
    final int rest = prefixBits % BITS_PER_BYTE;
    final int mask = 0xFF00 >> rest & 0xFF;
    return Arrays.equals(Arrays.copyOf(address, whole), Arrays.copyOf(network, whole))
        && (rest == 0 || (address[whole] & mask) == (network[whole] & mask));
  }
}
