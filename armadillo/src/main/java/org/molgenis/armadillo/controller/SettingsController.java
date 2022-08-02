package org.molgenis.armadillo.controller;

import static org.molgenis.armadillo.audit.AuditEventPublisher.*;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.molgenis.armadillo.audit.AuditEventPublisher;
import org.molgenis.armadillo.settings.ArmadilloSettingsService;
import org.molgenis.armadillo.settings.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
@SecurityRequirement(name = "http")
@SecurityRequirement(name = "JSESSIONID")
public class SettingsController {

  private ArmadilloSettingsService armadilloSettingsService;
  private AuditEventPublisher auditEventPublisher;

  @Autowired(required = false) // only set when oauth login is enabled
  private OAuth2AuthorizedClientService clientService;

  public SettingsController(
      ArmadilloSettingsService armadilloSettingsService, AuditEventPublisher auditEventPublisher) {
    this.armadilloSettingsService = armadilloSettingsService;
    this.auditEventPublisher = auditEventPublisher;
  }

  @Operation(
      summary = "Get project permissions",
      description =
          "List projects (key) and per project list user emails array having access (value)")
  @GetMapping(value = "/access", produces = APPLICATION_JSON_VALUE)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Found the permissions",
            content = {
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = Map.class),
                  examples =
                      @ExampleObject(
                          "{\n"
                              + "  \"myproject\": [\n"
                              + "    \"m.a.swertz@gmail.com\"\n"
                              + "  ]\n"
                              + "}"))
            }),
        @ApiResponse(responseCode = "403", description = "Permission denied", content = @Content)
      })
  public Map<String, Set<String>> accessList(Principal principal) {
    auditEventPublisher.audit(principal, LIST_ACCESS, Map.of());
    return armadilloSettingsService.projectList();
  }

  @Operation(
      summary = "Grant access to email on one project",
      description =
          "Permissions will be in effect when user signs in again. N.B. 'administrators' is a special project which will grant administrator permission to a user")
  @PostMapping(value = "/access", produces = TEXT_PLAIN_VALUE)
  @ResponseStatus(CREATED)
  public void accessAdd(
      Principal principal, @RequestParam String email, @RequestParam String project) {
    armadilloSettingsService.accessAdd(email, project);
    auditEventPublisher.audit(principal, GRANT_ACCESS, Map.of("project", project, "email", email));
  }

  @Operation(
      summary = "Revoke access from email on one project",
      description = "Permissions will be in effect when user signs in again.")
  @DeleteMapping(value = "/access", produces = TEXT_PLAIN_VALUE)
  @ResponseStatus(OK)
  public void accessDelete(
      Principal principal, @RequestParam String email, @RequestParam String project) {
    armadilloSettingsService.accessDelete(email, project);
    auditEventPublisher.audit(principal, REVOKE_ACCESS, Map.of("project", project, "email", email));
  }

  @Operation(summary = "Get raw information from the current user")
  @GetMapping("/my/principal")
  public AbstractAuthenticationToken myPrincipal(Principal principal) {
    return (AbstractAuthenticationToken) principal;
  }

  @Operation(summary = "Token of the current user")
  @GetMapping("/my/token")
  public String myToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof OAuth2AuthenticationToken) {
      OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
      OAuth2AuthorizedClient client =
          clientService.loadAuthorizedClient(
              oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
      return client.getAccessToken().getTokenValue();
    } else if (authentication instanceof JwtAuthenticationToken) {
      return ((JwtAuthenticationToken) authentication).getToken().getTokenValue();
    }
    throw new UnsupportedOperationException("couldn't get token");
  }

  @Operation(
      summary = "Get info on current user",
      description =
          "Get information on current user. Note, if you just gave yourself permission, you need to sign via /logout to refresh permissions")
  @GetMapping(value = "/my/access", produces = APPLICATION_JSON_VALUE)
  public List<String> myAccessList() {
    Collection<SimpleGrantedAuthority> authorities =
        (Collection<SimpleGrantedAuthority>)
            SecurityContextHolder.getContext().getAuthentication().getAuthorities();
    return authorities.stream()
        .filter(authority -> authority.getAuthority().endsWith("_RESEARCHER"))
        .map(authority -> authority.getAuthority().replace("_RESEARCHER", "").replace("ROLE_", ""))
        .toList();
  }

  @Operation(
      summary =
          "List users (key) and per user list details, such as firstName, lastName and projects",
      description = " projects:['administrators',...] means user is SU")
  @GetMapping(value = "/users", produces = APPLICATION_JSON_VALUE)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Found the permissions",
            content = {
              @Content(
                  mediaType = "application/json",
                  schema = @Schema(implementation = Map.class),
                  examples =
                      @ExampleObject(
                          "{\n"
                              + "  \"myproject\": [\n"
                              + "    \"m.a.swertz@gmail.com\"\n"
                              + "  ]\n"
                              + "}"))
            }),
        @ApiResponse(responseCode = "403", description = "Permission denied", content = @Content)
      })
  public Map<String, User> userList(Principal principal) {
    auditEventPublisher.audit(principal, LIST_USERS, Map.of());
    return armadilloSettingsService.userList();
  }

  @Operation(summary = "Add/Update user by email using email as id")
  @PutMapping(value = "/users/{email}", produces = TEXT_PLAIN_VALUE)
  @ResponseStatus(OK)
  public void userUpsert(Principal principal, @PathVariable String email, @RequestBody User user) {
    armadilloSettingsService.userUpsert(email, user);
    auditEventPublisher.audit(principal, UPSERT_USER, Map.of("email", email, "user", user));
  }

  @Operation(summary = "Delete user including details and permissions using email as id")
  @DeleteMapping(value = "/users/{email}", produces = TEXT_PLAIN_VALUE)
  @ResponseStatus(OK)
  public void userDelete(Principal principal, @PathVariable String email) {
    armadilloSettingsService.userDelete(email);
    auditEventPublisher.audit(principal, DELETE_USER, Map.of("email", email));
  }
}
