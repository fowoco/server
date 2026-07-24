/**
 * Provider-neutral boundary between the Server and the separately deployed AI Runtime.
 *
 * <p>This module owns the internal request/response contract and defensive validation. Prompt
 * assembly, model selection, Provider SDKs, and Knowledge content belong to the {@code ai} and
 * {@code knowledge} repositories.</p>
 */
package com.fowoco.server.aiintegration;
