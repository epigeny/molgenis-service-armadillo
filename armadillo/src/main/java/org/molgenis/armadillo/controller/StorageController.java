package org.molgenis.armadillo.controller;

import static org.molgenis.armadillo.audit.AuditEventPublisher.COPY_OBJECT;
import static org.molgenis.armadillo.audit.AuditEventPublisher.DELETE_OBJECT;
import static org.molgenis.armadillo.audit.AuditEventPublisher.DOWNLOAD_OBJECT;
import static org.molgenis.armadillo.audit.AuditEventPublisher.GET_OBJECT;
import static org.molgenis.armadillo.audit.AuditEventPublisher.LIST_OBJECTS;
import static org.molgenis.armadillo.audit.AuditEventPublisher.MOVE_OBJECT;
import static org.molgenis.armadillo.audit.AuditEventPublisher.OBJECT;
import static org.molgenis.armadillo.audit.AuditEventPublisher.PREVIEW_OBJECT;
import static org.molgenis.armadillo.audit.AuditEventPublisher.PROJECT;
import static org.molgenis.armadillo.audit.AuditEventPublisher.UPLOAD_OBJECT;
import static org.springframework.http.HttpStatus.NO_CONTENT;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.notFound;
import static org.springframework.web.bind.annotation.RequestMethod.HEAD;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import org.molgenis.armadillo.audit.AuditEventPublisher;
import org.molgenis.armadillo.exceptions.FileProcessingException;
import org.molgenis.armadillo.storage.ArmadilloStorageService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "storage", description = "API to manipulate the storage")
@RestController
@SecurityRequirement(name = "http")
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "JSESSIONID")
@RequestMapping("storage")
public class StorageController {

  private final ArmadilloStorageService storage;
  private final AuditEventPublisher auditor;

  public StorageController(ArmadilloStorageService storage, AuditEventPublisher auditor) {
    this.storage = storage;
    this.auditor = auditor;
  }

  @Operation(summary = "List objects in a project")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Objects listed",
            content =
                @Content(array = @ArraySchema(schema = @Schema(implementation = String.class)))),
        @ApiResponse(
            responseCode = "404",
            description = "Project does not exist",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  @GetMapping(
      value = "/projects/{project}/objects",
      produces = {APPLICATION_JSON_VALUE})
  @ResponseStatus(OK)
  public List<String> listObjects(Principal principal, @PathVariable String project) {
    return auditor.audit(
        () -> storage.listObjects(project), principal, LIST_OBJECTS, Map.of(PROJECT, project));
  }

  @Operation(summary = "Upload an object to a project")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Object uploaded successfully"),
        @ApiResponse(responseCode = "404", description = "Unknown project"),
        @ApiResponse(responseCode = "409", description = "Object already exists"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @PostMapping(
      value = "/projects/{project}/objects",
      consumes = {MULTIPART_FORM_DATA_VALUE})
  @ResponseStatus(NO_CONTENT)
  public void uploadObject(
      Principal principal,
      @PathVariable String project,
      @RequestParam @NotEmpty String object,
      @Valid @RequestParam MultipartFile file) {
    auditor.audit(
        () -> addObject(project, object, file),
        principal,
        UPLOAD_OBJECT,
        Map.of(PROJECT, project, OBJECT, object));
  }

  private void addObject(String project, String object, MultipartFile file) {
    try {
      storage.addObject(project, object, file.getInputStream());
    } catch (IOException e) {
      throw new FileProcessingException();
    }
  }

  @Operation(
      summary = "Copy an object within a project",
      description =
          "The request body should contain the new object's name in full (e.g. core/nonrep.parquet)")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Object copied successfully"),
        @ApiResponse(responseCode = "404", description = "Unknown project or object"),
        @ApiResponse(responseCode = "409", description = "Object already exists"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @PostMapping(
      value = "/projects/{project}/objects/{object}/copy",
      consumes = {APPLICATION_JSON_VALUE})
  @ResponseStatus(NO_CONTENT)
  public void copyObject(
      Principal principal,
      @PathVariable String project,
      @PathVariable String object,
      @RequestBody ObjectRequestBody requestBody) {
    auditor.audit(
        () -> storage.copyObject(project, requestBody.name(), object),
        principal,
        COPY_OBJECT,
        Map.of(PROJECT, project, "from", object, "to", requestBody.name()));
  }

  @Operation(
      summary = "Move an object within a project",
      description =
          "The request body should contain the new object's name in full (e.g. core/nonrep.parquet)")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Object moved successfully"),
        @ApiResponse(responseCode = "404", description = "Unknown project or object"),
        @ApiResponse(responseCode = "409", description = "Object already exists"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @PostMapping(
      value = "/projects/{project}/objects/{object}/move",
      consumes = {APPLICATION_JSON_VALUE})
  @ResponseStatus(NO_CONTENT)
  public void moveObject(
      Principal principal,
      @PathVariable String project,
      @PathVariable String object,
      @Valid @RequestBody ObjectRequestBody requestBody) {
    auditor.audit(
        () -> storage.moveObject(project, requestBody.name(), object),
        principal,
        MOVE_OBJECT,
        Map.of(PROJECT, project, "from", object, "to", requestBody.name()));
  }

  @Operation(summary = "Object exists?")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Object exists"),
        @ApiResponse(responseCode = "404", description = "Object does not exist"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @RequestMapping(value = "/projects/{project}/objects/{object}", method = HEAD)
  public ResponseEntity<Void> objectExists(
      Principal principal, @PathVariable String project, @PathVariable String object) {
    boolean objectExists =
        auditor.audit(
            () -> storage.hasObject(project, object),
            principal,
            GET_OBJECT,
            Map.of(PROJECT, project, OBJECT, object));
    return objectExists ? noContent().build() : notFound().build();
  }

  @Operation(summary = "Retrieve first 10 rows of the data?")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Preview succes"),
        @ApiResponse(responseCode = "404", description = "Object does not exist"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @GetMapping(
      path = "/projects/{project}/objects/{object}/preview",
      produces = APPLICATION_JSON_VALUE)
  public @ResponseBody List<Map<String, String>> previewObject(
      Principal principal, @PathVariable String project, @PathVariable String object) {
    return auditor.audit(
        () -> storage.getPreview(project, object),
        principal,
        PREVIEW_OBJECT,
        Map.of(PROJECT, project, OBJECT, object));
  }

  @Operation(summary = "Delete an object")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Object deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Unknown project or object"),
        @ApiResponse(responseCode = "401", description = "Unauthorized")
      })
  @DeleteMapping("/projects/{project}/objects/{object}")
  @ResponseStatus(NO_CONTENT)
  public void deleteObject(
      Principal principal, @PathVariable String project, @PathVariable String object) {
    auditor.audit(
        () -> storage.deleteObject(project, object),
        principal,
        DELETE_OBJECT,
        Map.of(PROJECT, project, OBJECT, object));
  }

  @Operation(summary = "Download an object")
  @PreAuthorize("hasAnyRole('ROLE_SU', 'ROLE_' + #project.toUpperCase() + '_RESEARCHER')")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Object downloaded successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Unknown project or object",
            content = @Content(schema = @Schema(hidden = true))),
        @ApiResponse(
            responseCode = "401",
            description = "Unauthorized",
            content = @Content(schema = @Schema(hidden = true)))
      })
  @GetMapping(
      value = "/projects/{project}/objects/{object}",
      produces = {APPLICATION_OCTET_STREAM_VALUE})
  public @ResponseBody ResponseEntity<ByteArrayResource> downloadObject(
      Principal principal, @PathVariable String project, @PathVariable String object) {
    return auditor.audit(
        () -> getObject(project, object),
        principal,
        DOWNLOAD_OBJECT,
        Map.of(PROJECT, project, OBJECT, object));
  }

  @PreAuthorize("hasAnyRole('ROLE_SU', 'ROLE_' + #project.toUpperCase() + '_RESEARCHER')")
  private ResponseEntity<ByteArrayResource> getObject(String project, String object) {
    var inputStream = storage.loadObject(project, object);
    var objectParts = object.split("/");
    var fileName = objectParts[objectParts.length - 1];

    try {
      InputStreamResource inputStreamResource = new InputStreamResource(inputStream);
      long length = inputStream.available();
      ContentDisposition contentDisposition =
          ContentDisposition.attachment().filename(fileName).build();
      HttpHeaders httpHeaders = new HttpHeaders();
      httpHeaders.setContentDisposition(contentDisposition);
      httpHeaders.setContentLength(length);
      httpHeaders.setContentType(APPLICATION_OCTET_STREAM);
      return new ResponseEntity(inputStreamResource, httpHeaders, HttpStatus.OK);
    } catch (IOException e) {
      throw new FileProcessingException();
    }
  }
}
