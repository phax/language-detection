package com.carrotsearch.labs.langid;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.carrotsearch.hppc.cursors.ObjectObjectCursor;
import com.google.common.collect.Lists;
import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;

/**
 * EuroParl test data.
 */
public final class EuroParlData
{
  private EuroParlData ()
  {}

  public static List <ObjectObjectCursor <String, String>> europarl21 () throws IOException
  {
    return readTabDelimited (Resources.getResource (EuroParlData.class, "/europarl.21.test"));
  }

  public static List <ObjectObjectCursor <String, String>> europarl18 () throws IOException
  {
    return readTabDelimited (Resources.getResource (EuroParlData.class, "/europarl.18.test"));
  }

  private static List <ObjectObjectCursor <String, String>> readTabDelimited (final URL resource) throws IOException
  {
    return Resources.readLines (resource,
                                StandardCharsets.UTF_8,
                                new LineProcessor <List <ObjectObjectCursor <String, String>>> ()
                                {
                                  private final List <ObjectObjectCursor <String, String>> list = Lists.newArrayList ();

                                  @Override
                                  public boolean processLine (final String line) throws IOException
                                  {
                                    final int tabIndex = line.indexOf ('\t');
                                    if (tabIndex < 0)
                                      throw new IOException ("Expected a tab on every line: " + line);
                                    final ObjectObjectCursor <String, String> c = new ObjectObjectCursor <> ();
                                    c.key = line.substring (0, tabIndex);
                                    c.value = line.substring (tabIndex + 1);
                                    list.add (c);
                                    return true;
                                  }

                                  @Override
                                  public List <ObjectObjectCursor <String, String>> getResult ()
                                  {
                                    return list;
                                  }
                                });
  }
}
