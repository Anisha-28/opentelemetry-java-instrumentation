/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.netty.v4_0.client;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.instrumentation.auto.netty.v4_0.client.NettyHttpClientTracer.TRACER;

import io.grpc.Context;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.HttpRequest;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.utils.NetPeerUtils;
import io.opentelemetry.instrumentation.auto.netty.v4_0.AttributeKeys;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;

public class HttpClientRequestTracingHandler extends ChannelOutboundHandlerAdapter {

  @Override
  public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise prm) {
    if (!(msg instanceof HttpRequest)) {
      ctx.write(msg, prm);
      return;
    }

    // TODO pass Context into Tracer.startSpan() and then don't need this scoping
    Scope parentScope = null;
    Context parentContext =
        ctx.channel().attr(AttributeKeys.PARENT_CONNECT_CONTEXT_ATTRIBUTE_KEY).getAndRemove();
    if (parentContext != null) {
      parentScope = withScopedContext(parentContext);
    }

    HttpRequest request = (HttpRequest) msg;

    ctx.channel().attr(AttributeKeys.CLIENT_PARENT_ATTRIBUTE_KEY).set(Context.current());

    Span span = TRACER.startSpan(request);
    NetPeerUtils.setNetPeer(span, (InetSocketAddress) ctx.channel().remoteAddress());
    ctx.channel().attr(AttributeKeys.CLIENT_ATTRIBUTE_KEY).set(span);

    try (Scope scope = TRACER.startScope(span, request.headers())) {
      ctx.write(msg, prm);
    } catch (Throwable throwable) {
      TRACER.endExceptionally(span, throwable);
      throw throwable;
    } finally {
      if (null != parentScope) {
        parentScope.close();
      }
    }
  }
}
