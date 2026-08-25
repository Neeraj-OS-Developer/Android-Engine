package com.tom.rv2ide.fuzzysearch.algorithms;

import com.tom.rv2ide.fuzzysearch.StringProcessor;

/**
 * @deprecated Use {@code ToStringFunction#NO_PROCESS} instead.
 */
@Deprecated
public class NoProcess extends StringProcessor {

  @Override
  @Deprecated
  public String process(String in) {
    return in;
  }
}
