package com.ikk.backend.features.assets.api

import com.ikk.backend.features.assets.application.AssetService
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/v1")
class AssetController(private val assets: AssetService) {
    @PostMapping(
        "/projects/{projectId}/assets",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
    )
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(
        @PathVariable projectId: String,
        @RequestParam("file") file: MultipartFile,
    ): AssetUploadedResponse {
        val asset = assets.upload(projectId, file)
        return AssetUploadedResponse(asset.ref, asset.mime, asset.bytes)
    }

    @GetMapping("/assets/{ref}")
    fun get(@PathVariable ref: String): ResponseEntity<ByteArray> {
        val asset = assets.require(ref)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(asset.mime))
            .contentLength(asset.bytes)
            .cacheControl(CacheControl.noCache())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(asset.originalName).build().toString(),
            )
            .body(asset.content)
    }
}

data class AssetUploadedResponse(val ref: String, val mime: String, val bytes: Long)
