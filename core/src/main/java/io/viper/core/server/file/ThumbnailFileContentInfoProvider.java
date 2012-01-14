package io.viper.core.server.file;


import com.thebuzzmedia.imgscalr.Scalr;
import io.viper.core.server.Util;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.imageio.ImageIO;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.json.JSONException;
import org.json.JSONObject;


// TODO: do this using chaining
public class ThumbnailFileContentInfoProvider implements FileContentInfoProvider
{
  private String _srcRootPath;
  private String _rootPath;
  String _metaFilePath;

  public static ThumbnailFileContentInfoProvider create(String srcRootPath)
  {
    return new ThumbnailFileContentInfoProvider(srcRootPath);
  }

  public ThumbnailFileContentInfoProvider(String srcRootPath)
  {
    _srcRootPath = srcRootPath.endsWith(File.separator) ? srcRootPath : srcRootPath + File.separator;
    _rootPath = srcRootPath + File.separator + ".thumb" + File.separatorChar;
    _metaFilePath = _rootPath + File.separatorChar + ".meta" + File.separatorChar;

    new File(_rootPath).mkdirs();
    new File(_metaFilePath).mkdirs();
  }

  @Override
  public FileContentInfo getFileContent(String path)
  {
    FileChannel fc = null;
    FileContentInfo result = null;

    // TODO: make a touch more secure - chroot to the rescue!
    try
    {
      final String fullPath = _rootPath + path + ".jpg";

      if (!fullPath.endsWith("/"))
      {
        File file;

        file = new File(fullPath);

        if (!file.exists())
        {
          // attempt to resize it
          File srcFile = new File(_srcRootPath + path);

          if (srcFile.exists())
          {
            BufferedImage image = ImageIO.read(srcFile);

            BufferedImage thumbnail = Scalr.resize(
                image,
                Scalr.Method.SPEED,
                Scalr.Mode.FIT_TO_WIDTH,
                150,
                100,
                Scalr.OP_ANTIALIAS);

            ImageIO.write(thumbnail, "jpg", file);
          }
        }

        if (file.exists())
        {
          Map<String, String> meta = new HashMap<String, String>();

          File metaFile = new File(_metaFilePath + path);
          if (metaFile.exists())
          {
            RandomAccessFile metaRaf = new RandomAccessFile(metaFile, "r");
            String rawJSON = metaRaf.readUTF();
            JSONObject jsonObject = new JSONObject(rawJSON);
            metaRaf.close();

            Iterator<String> keys = jsonObject.keys();
            while(keys.hasNext())
            {
              String key = keys.next();
              meta.put(key, jsonObject.getString(key));
            }
          }
          else
          {
            meta.put(HttpHeaders.Names.CONTENT_TYPE, Util.getContentType(file.getName()));
            meta.put(HttpHeaders.Names.CONTENT_LENGTH, Long.toString(file.length()));
          }

          result = FileContentInfo.create(file, meta);
        }
      }
    }
    catch (IOException e)
    {
      e.printStackTrace();
    }
    catch (JSONException e)
    {
      e.printStackTrace();
    }
    finally
    {
      if (result == null)
      {
        if (fc != null)
        {
          try
          {
            fc.close();
          }
          catch (IOException e)
          {
            e.printStackTrace();
          }
        }
      }
    }

    return result;
  }

  @Override
  public void dispose(FileContentInfo info)
  {
    if (info.fileChannel != null)
    {
      try
      {
        info.fileChannel.close();
        info.content.clear();
      }
      catch (IOException e)
      {
        e.printStackTrace();
      }
    }
  }
}
