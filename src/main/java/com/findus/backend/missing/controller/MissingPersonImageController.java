package com.findus.backend.missing.controller;

import com.findus.backend.missing.repository.MissingPersonImageRepository;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Tag(name="실종자",description="자료 출처: 경찰청")
@RequestMapping("/api/missing-persons/{personId}/images")
public class MissingPersonImageController {
    private final MissingPersonImageRepository images;
    public MissingPersonImageController(MissingPersonImageRepository images) { this.images=images; }
    @GetMapping(value="/{imageId}",produces={"image/jpeg","image/png"})
    @Transactional(readOnly=true)
    @Operation(summary="저장된 실종아동 사진",description="쿠키 없이 DB에 저장된 사진을 반환합니다. 다른 아동의 사진·공개 중단·삭제된 사진은 404입니다.")
    public ResponseEntity<byte[]> image(@PathVariable UUID personId,@PathVariable UUID imageId) {
        var image=images.findPublic(personId,imageId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"사진을 찾을 수 없습니다."));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.getContentType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff")
                .contentLength(image.getByteSize()).body(image.getContent());
    }
}
