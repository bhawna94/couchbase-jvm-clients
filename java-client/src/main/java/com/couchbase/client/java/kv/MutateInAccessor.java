package com.couchbase.client.java.kv;

import com.couchbase.client.core.Core;
import com.couchbase.client.core.error.CouchbaseException;
import com.couchbase.client.core.msg.kv.SubdocMutateRequest;

import java.util.concurrent.CompletableFuture;

public class MutateInAccessor {

  public static CompletableFuture<MutateInResult> mutateIn(Core core, SubdocMutateRequest request) {
    core.send(request);
    return request
      .response()
      .thenApply(response -> {
        switch (response.status()) {
          case SUCCESS:
            return new MutateInResult(response.cas(), response.mutationToken());
          default:
            throw new CouchbaseException("Unexpected Status Code " + response.status());
        }
      });
  }
}