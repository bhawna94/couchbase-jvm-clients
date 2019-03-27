/*
 * Copyright (c) 2019 Couchbase, Inc.
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

package com.couchbase.client.core.msg.view;

import com.couchbase.client.core.msg.BaseResponse;
import com.couchbase.client.core.msg.ResponseStatus;
import com.couchbase.client.core.msg.chunk.ChunkedResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ViewResponse
  extends BaseResponse
  implements ChunkedResponse<ViewChunkHeader, ViewChunkRow, ViewChunkTrailer> {

  private final ViewChunkHeader header;
  private final Flux<ViewChunkRow> rows;
  private final Mono<ViewChunkTrailer> trailer;

  ViewResponse(final ResponseStatus status, final ViewChunkHeader header,
                final Flux<ViewChunkRow> rows, final Mono<ViewChunkTrailer> trailer) {
    super(status);
    this.header = header;
    this.rows = rows;
    this.trailer = trailer;
  }

  @Override
  public ViewChunkHeader header() {
    return header;
  }

  @Override
  public Flux<ViewChunkRow> rows() {
    return rows;
  }

  @Override
  public Mono<ViewChunkTrailer> trailer() {
    return trailer;
  }

  @Override
  public String toString() {
    return "ViewResponse{" +
      "header=" + header +
      ", rows=" + rows +
      ", trailer=" + trailer +
      '}';
  }
}

