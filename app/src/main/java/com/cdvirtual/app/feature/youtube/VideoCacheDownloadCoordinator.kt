    suspend fun download(
        cacheId: String,
        request: VideoCacheTransferRequest,
        onProgress: (VideoCacheTransferProgress) -> Unit = {}
    ): Unit {        transferSemaphore.withPermit {
    
            manager.startDownload(cacheId)
            try {
                val partial = transfer.download(cacheId, request, onProgress)
                manager.publishDownloadedFile(
                    cacheId = cacheId,
                    partialFile = partial,
                    extension = request.extension,
                    expectedBytes = request.expectedBytes
                )
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    runCatching { manager.markInterrupted(cacheId) }
                }
                throw cancelled
            } catch (failure: Exception) {
                withContext(NonCancellable) {
                    runCatching { manager.markInterrupted(cacheId) }
                }
                throw failure
            }
        }
    }
}


    companion object {
        const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 3
    }
}