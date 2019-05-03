/*
 * Copyright (c) 2018 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.couchbase.client.core.msg.kv;

import com.couchbase.client.core.CoreContext;
import com.couchbase.client.core.env.CompressionConfig;
import com.couchbase.client.core.error.DurabilityLevelNotAvailableException;
import com.couchbase.client.core.io.netty.kv.ChannelContext;
import com.couchbase.client.core.io.netty.kv.MemcacheProtocol;
import com.couchbase.client.core.msg.ResponseStatus;
import com.couchbase.client.core.retry.RetryStrategy;
import com.couchbase.client.core.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.core.deps.io.netty.buffer.ByteBufAllocator;
import com.couchbase.client.core.deps.io.netty.buffer.Unpooled;

import java.time.Duration;
import java.util.Optional;

import static com.couchbase.client.core.io.netty.kv.MemcacheProtocol.cas;
import static com.couchbase.client.core.io.netty.kv.MemcacheProtocol.extractToken;
import static com.couchbase.client.core.io.netty.kv.MemcacheProtocol.flexibleSyncReplication;
import static com.couchbase.client.core.io.netty.kv.MemcacheProtocol.noCas;

/**
 * Uses the KV replace command to replace a document if it exists.
 *
 * @since 2.0.0
 */
public class ReplaceRequest extends BaseKeyValueRequest<ReplaceResponse> {

  private final byte[] content;
  private final long expiration;
  private final int flags;
  private final long cas;
  private final Optional<DurabilityLevel> syncReplicationType;

  public ReplaceRequest(final String key, final byte[] collection, final byte[] content, final long expiration,
                        final int flags, final Duration timeout,
                        final long cas, final CoreContext ctx, final String bucket,
                        final RetryStrategy retryStrategy,
                        final Optional<DurabilityLevel> syncReplicationType) {
    super(timeout, ctx, bucket, retryStrategy, key, collection);
    this.content = content;
    this.expiration = expiration;
    this.flags = flags;
    this.cas = cas;
    this.syncReplicationType = syncReplicationType;
  }

  @Override
  public ByteBuf encode(ByteBufAllocator alloc, int opaque, ChannelContext ctx) {
    ByteBuf key = Unpooled.wrappedBuffer(ctx.collectionsEnabled() ? keyWithCollection() : key());

    byte datatype = 0;
    ByteBuf content;
    CompressionConfig config = ctx.compressionConfig();
    if (config != null && config.enabled() && this.content.length >= config.minSize()) {
      ByteBuf maybeCompressed = MemcacheProtocol.tryCompression(this.content, config.minRatio());
      if (maybeCompressed != null) {
        datatype |= MemcacheProtocol.Datatype.SNAPPY.datatype();
        content = maybeCompressed;
      } else {
        content = Unpooled.wrappedBuffer(this.content);
      }
    } else {
      content = Unpooled.wrappedBuffer(this.content);
    }

    ByteBuf extras = alloc.buffer(Integer.BYTES * 2);
    extras.writeInt(flags);
    extras.writeInt((int) expiration);

    ByteBuf request;
    if (syncReplicationType.isPresent()) {
      if (ctx.syncReplicationEnabled()) {
        ByteBuf flexibleExtras = flexibleSyncReplication(alloc, syncReplicationType.get(), timeout());
        request = MemcacheProtocol.flexibleRequest(alloc, MemcacheProtocol.Opcode.REPLACE, datatype, partition(),
                opaque, cas, flexibleExtras, extras, key, content);
        flexibleExtras.release();
      }
      else {
        throw new DurabilityLevelNotAvailableException(syncReplicationType.get());
      }
    } else {
      request = MemcacheProtocol.request(alloc, MemcacheProtocol.Opcode.REPLACE, datatype, partition(),
        opaque, cas, extras, key, content);
    }

    key.release();
    extras.release();
    content.release();

    return request;
  }

  @Override
  public ReplaceResponse decode(final ByteBuf response, ChannelContext ctx) {
    ResponseStatus status = MemcacheProtocol.decodeStatus(response);
    return new ReplaceResponse(
      status,
      cas(response),
      extractToken(ctx.mutationTokensEnabled(), partition(), response, ctx.bucket())
    );
  }
}
