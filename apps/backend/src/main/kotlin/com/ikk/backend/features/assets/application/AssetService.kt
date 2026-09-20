package com.ikk.backend.features.assets.application

import com.ikk.backend.features.assets.domain.Asset
import com.ikk.backend.features.assets.infrastructure.AssetRepository
import com.ikk.backend.features.projects.application.ProjectService
import com.ikk.backend.shared.api.ApiException
import com.ikk.backend.shared.ids.IdGenerator
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.time.Instant

@Service
class AssetService(
    private val projects: ProjectService,
    private val assets: AssetRepository,
    private val ids: IdGenerator,
    @Value("\${ikk.asset-max-bytes:10485760}") private val maxBytes: Long,
) {
    fun upload(projectId: String, file: MultipartFile): Asset {
        projects.require(projectId)
        if (file.isEmpty) {
            throw ApiException(HttpStatus.BAD_REQUEST, "empty_asset", "uploaded asset is empty")
        }
        if (file.size > maxBytes) {
            throw ApiException(
                HttpStatus.CONTENT_TOO_LARGE,
                "asset_too_large",
                "asset exceeds $maxBytes bytes",
            )
        }
        val mime = file.contentType?.takeIf(SUPPORTED::contains) ?: throw ApiException(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "unsupported_asset_type",
            "supported asset types are PNG, JPEG, and WebP",
        )
        return try {
            Asset(
                ref = ids.next("asset_"),
                projectId = projectId,
                originalName = file.originalFilename.safeName(),
                mime = mime,
                bytes = file.size,
                content = file.bytes,
                createdAt = Instant.now(),
            ).also(assets::insert)
        } catch (_: IOException) {
            throw ApiException(HttpStatus.BAD_REQUEST, "asset_read_failed", "could not read uploaded asset")
        }
    }

    fun require(ref: String): Asset = assets.find(ref) ?: throw ApiException(
        HttpStatus.NOT_FOUND,
        "asset_not_found",
        "asset $ref was not found",
    )

    private fun String?.safeName(): String =
        (this?.replace('\\', '/')?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "asset")
            .take(500)

    companion object {
        private val SUPPORTED = setOf("image/png", "image/jpeg", "image/webp")
    }
}
