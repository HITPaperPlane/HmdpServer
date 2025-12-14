package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename, "blogs");
            File target = new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            // 保存文件
            image.transferTo(target);
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok("/" + fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @PostMapping("avatar")
    public Result uploadAvatar(@RequestParam("file") MultipartFile image) {
        try {
            String originalFilename = image.getOriginalFilename();
            String fileName = createNewFileName(originalFilename, "avatars");
            File target = new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            image.transferTo(target);
            log.debug("头像上传成功，{}", fileName);
            return Result.ok("/" + fileName);
        } catch (IOException e) {
            throw new RuntimeException("头像上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        String relative = normalizeRelativePath(filename);
        if (StrUtil.isBlank(relative)) {
            return Result.fail("错误的文件名称");
        }
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, relative);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename, String bizDir) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        if (StrUtil.isBlank(suffix)) {
            suffix = "png";
        }
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("{}/{}/{}", bizDir, d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("{}/{}/{}/{}.{}", bizDir, d1, d2, name, suffix);
    }

    private String normalizeRelativePath(String path) {
        if (StrUtil.isBlank(path)) {
            return null;
        }
        String p = path.trim().replace("\\", "/");
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.contains("..")) {
            return null;
        }
        return p;
    }
}
