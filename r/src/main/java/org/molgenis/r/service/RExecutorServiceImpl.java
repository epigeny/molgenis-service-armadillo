package org.molgenis.r.service;

import static java.lang.String.format;
import static org.apache.commons.io.FileUtils.byteCountToDisplaySize;

import com.google.common.base.Stopwatch;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Principal;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.commons.io.IOUtils;
import org.molgenis.r.Formatter;
import org.molgenis.r.RServerConnection;
import org.molgenis.r.RServerException;
import org.molgenis.r.RServerResult;
import org.molgenis.r.exceptions.FailedRPackageInstallException;
import org.molgenis.r.exceptions.InvalidRPackageException;
import org.molgenis.r.exceptions.RExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class RExecutorServiceImpl implements RExecutorService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RExecutorServiceImpl.class);
  public static final int RFILE_BUFFER_SIZE = 65536;

  @Override
  public RServerResult execute(String cmd, RServerConnection connection) {
    try {
      LOGGER.debug("Evaluate {}", cmd);
      RServerResult result = connection.eval(format("try({%s})", cmd));
      if (result == null) {
        throw new RExecutionException("Eval returned null");
      }
      return result;
    } catch (RServerException e) {
      throw new RExecutionException(e);
    }
  }

  @Override
  public void saveWorkspace(
      RServerConnection connection, Consumer<InputStream> inputStreamConsumer) {
    try {
      LOGGER.debug("Save workspace");
      String command = "base::save.image()";
      execute(command, connection);
      try (InputStream is = connection.openFile(".RData")) {
        inputStreamConsumer.accept(is);
      }
    } catch (IOException e) {
      throw new RExecutionException(e);
    }
  }

  @Override
  public void loadWorkspace(RServerConnection connection, Resource resource, String environment) {
    LOGGER.debug("Load workspace into {}", environment);
    try {
      copyFile(resource, ".RData", connection);
      connection.eval(format("base::load(file='.RData', envir=%s)", environment));
      connection.eval("base::unlink('.RData')");
    } catch (IOException | RServerException e) {
      throw new RExecutionException(e);
    }
  }

  @Override
  public void loadTable(
      RServerConnection connection,
      Resource resource,
      String filename,
      String symbol,
      List<String> variables) {
    LOGGER.debug("Load table from file {} into {}", filename, symbol);
    String rFileName = filename.replace("/", "_");
    try {
      copyFile(resource, rFileName, connection);
      if (variables.isEmpty()) {
        execute(
            format(
                "is.null(base::assign('%s', value={arrow::read_parquet('%s')}))",
                symbol, rFileName),
            connection);
      } else {
        String colSelect =
            "tidyselect::any_of("
                + Formatter.stringVector(variables.toArray(new String[] {}))
                + ")";
        execute(
            format(
                "is.null(base::assign('%s', value={arrow::read_parquet('%s', col_select = %s)}))",
                symbol, rFileName, colSelect),
            connection);
      }
      execute(format("base::unlink('%s')", rFileName), connection);
    } catch (IOException e) {
      throw new RExecutionException(e);
    }
  }

  @Override
  public void loadResource(
      Principal principal,
      RServerConnection connection,
      Resource resource,
      String filename,
      String symbol) {
    LOGGER.debug("Load resource from file {} into {}", filename, symbol);
    String rFileName = filename.replace("/", "_");
    try {
      if (principal instanceof JwtAuthenticationToken token) {
        String tokenValue = token.getToken().getTokenValue();
        copyFile(resource, rFileName, connection);
        execute(format("is.null(base::assign('rds',base::readRDS('%s')))", rFileName), connection);
        execute(format("base::unlink('%s')", rFileName), connection);
        execute(
            format(
                """
                                  is.null(base::assign('R', value={resourcer::newResource(
                                          name = rds$name,
                                          url = rds$url,
                                          format = rds$format,
                                          secret = "%s"
                                  )}))""",
                tokenValue),
            connection);
      }
      execute(
          format("is.null(base::assign('%s', value={resourcer::newResourceClient(R)}))", symbol),
          connection);
    } catch (Exception e) {
      throw new RExecutionException(e);
    }
  }

  @Override
  public void installPackage(
      RServerConnection connection, Resource packageResource, String filename) {
    // https://stackoverflow.com/questions/30989027/how-to-install-a-package-from-a-download-zip-file

    if (!filename.endsWith(".tar.gz")) {
      throw new InvalidRPackageException(filename);
    }

    String packageName = getPackageNameFromFilename(filename);

    LOGGER.info("Installing package '{}'", filename);
    String rFilename = getRFilenameFromFilename(filename);
    try {
      copyFile(packageResource, rFilename, connection);
      execute(
          format("remotes::install_local('%s', dependencies = TRUE, upgrade = 'never')", rFilename),
          connection);
      RServerResult result = execute(format("require('%s')", packageName), connection);
      if (!result.asLogical()) {
        throw new FailedRPackageInstallException(packageName);
      }
      execute(format("file.remove('%s')", filename), connection);

    } catch (IOException e) {
      throw new RExecutionException(e);
    }
  }

  void copyFile(Resource resource, String dataFileName, RServerConnection connection)
      throws IOException {
    LOGGER.info("Copying '{}' to R...", dataFileName);
    Stopwatch sw = Stopwatch.createStarted();
    try (InputStream is = resource.getInputStream();
        OutputStream os = connection.createFile(dataFileName);
        BufferedOutputStream bos = new BufferedOutputStream(os, RFILE_BUFFER_SIZE)) {
      long size = IOUtils.copyLarge(is, bos);
      if (LOGGER.isDebugEnabled()) {
        var elapsed = sw.elapsed(TimeUnit.MICROSECONDS);
        LOGGER.debug(
            "Copied {} in {}ms [{} MB/s]",
            byteCountToDisplaySize(size),
            elapsed / 1000,
            format("%.03f", size * 1.0 / elapsed));
      }
    }
  }

  protected String getPackageNameFromFilename(String filename) {
    return filename.replaceFirst("_[^_]+$", "");
  }

  protected String getRFilenameFromFilename(String filename) {
    return filename.replace("/", "_");
  }
}
