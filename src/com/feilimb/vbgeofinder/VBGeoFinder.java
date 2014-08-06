package com.feilimb.vbgeofinder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.NTCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.util.EntityUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class VBGeoFinder
{
   private String VBF_HOST;
   private int VBF_PORT;
   private String VBF_USER;
   private String VBF_PASSWD;
   private String LOCAL_DOMAIN;
   private String LOCAL_WORKSTATION;
   private String LOCAL_PASSWD;
   private String LOCAL_USER;
   private String LOCAL_PROXY_HOST;
   private int LOCAL_PROXY_PORT;
   private BasicCookieStore cookieStore;
   private CloseableHttpClient httpClient;
   private boolean _debug = true;

   public static void main(String[] args)
   {
      VBGeoFinder mkf = new VBGeoFinder();
      mkf.start();
   }

   private void start()
   {
      initProperties();
      boolean useProxy = LOCAL_PROXY_HOST != null;
      initHttpClient(useProxy, true);

      login();
      Collection<FThread> fThreads = collectThreads(31, 1);
      parseThreads(fThreads);
      analyseImages(fThreads);
      dumpUsefulInfo(fThreads);
      dumpAllImgURLsToFile(fThreads);

      try
      {
         httpClient.close();
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
   }

   private void initProperties()
   {
      Properties prop = new Properties();
      try
      {
         // load our properties file
         prop.load(new FileInputStream("config.properties"));

         VBF_HOST = prop.getProperty("VBF_HOST");
         VBF_PORT = Integer.parseInt(prop.getProperty("VBF_PORT"));
         VBF_USER = prop.getProperty("VBF_USER");
         VBF_PASSWD = prop.getProperty("VBF_PASSWD");
         LOCAL_PROXY_HOST = prop.getProperty("LOCAL_PROXY_HOST");
         if (LOCAL_PROXY_HOST != null)
         {
            LOCAL_PROXY_PORT = Integer.parseInt(prop.getProperty("LOCAL_PROXY_PORT"));
            LOCAL_DOMAIN = prop.getProperty("LOCAL_DOMAIN");
            LOCAL_WORKSTATION = prop.getProperty("LOCAL_WORKSTATION");
            LOCAL_PASSWD = prop.getProperty("LOCAL_PASSWD");
            LOCAL_USER = prop.getProperty("LOCAL_USER");
         }
      }
      catch (FileNotFoundException e)
      {
         throw new RuntimeException(e);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
   }

   private void initHttpClient(boolean useProxy, boolean setCookieStore)
   {
      HttpClientBuilder clientBuilder = HttpClientBuilder.create();

      if (useProxy)
      {
         NTCredentials ntCreds = new NTCredentials(LOCAL_USER, LOCAL_PASSWD, LOCAL_WORKSTATION, LOCAL_DOMAIN);
         CredentialsProvider credsProvider = new BasicCredentialsProvider();
         credsProvider.setCredentials(new AuthScope(LOCAL_PROXY_HOST, LOCAL_PROXY_PORT), ntCreds);
         clientBuilder.setProxy(new HttpHost(LOCAL_PROXY_HOST, LOCAL_PROXY_PORT));
         clientBuilder.setDefaultCredentialsProvider(credsProvider);
         clientBuilder.setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
      }      
      
      clientBuilder.useSystemProperties();
      
      if (setCookieStore)
      {
         cookieStore = new BasicCookieStore();
         clientBuilder.setDefaultCookieStore(cookieStore);
      }
      
      httpClient = clientBuilder.build();
   }
   
   private void login()
   {
      CloseableHttpResponse response = null;
      try
      {
         HttpUriRequest login = RequestBuilder.post()
               .setUri(new URI("http://" + VBF_HOST + "/login.php"))
               .addParameter("vb_login_username", VBF_USER)
               .addParameter("vb_login_password", VBF_PASSWD)
               .addParameter("cookieuser", "1")
               .addParameter("submit", "Login")
               .addParameter("s", "")
               .addParameter("do", "login")
               .addParameter("forceredirect", "0")
               .addParameter("vb_login_md5password", "")
               .addParameter("vb_login_md5password_utf", "")
               .build();
         response = httpClient.execute(login);
         HttpEntity entity = response.getEntity();
   
         System.out.println("----------------------------------------");
         System.out.println(response.getStatusLine());
         EntityUtils.consume(entity);
   
         System.out.println("Cookies:");
         List<Cookie> cookies = cookieStore.getCookies();
         if (_debug)
         {
            for (int i = 0; i < cookies.size(); i++)
            {
               System.out.println("- " + cookies.get(i).toString());
            }
         }
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
      finally
      {
         if (response != null)
         {
            try
            {
               response.close();
            }
            catch (IOException e)
            {
               e.printStackTrace();
            }
         }
      }      
   }

   private Collection<FThread> collectThreads(int forumNum, int numPages)
   {
      Collection<FThread> fThreads = new ArrayList<FThread>();
      Pattern p = Pattern.compile("(thread_title_)(\\d+)");
      Matcher m = null;
      for (int pageNum=1; pageNum <= numPages; pageNum++)
      {
         URI url = buildForumSectionURI(forumNum, pageNum);
         try
         {
            StringBuilder pageSource = httpGetWithResponse(url);
            Document doc = Jsoup.parse(pageSource.toString());
            Element element = doc.getElementById("threads");
            Elements threadLinks = element.getElementsByTag("a");
            for (Element e : threadLinks)
            {
               m = p.matcher(e.id());
               if (m.matches())
               {
                  String threadId = m.group(2);
                  fThreads.add(new FThread(e.text(), buildPageThreadURI(threadId), threadId));
               }
            }
         }
         catch (Exception e)
         {
            e.printStackTrace();
            return null;
         }
      }
      
      return fThreads;
   }

   private void parseThreads(Collection<FThread> fThreads)
   {
      Iterator<FThread> iter = fThreads.iterator();
      while (iter.hasNext())
      {
         FThread ft = iter.next();
         Collection<URI> imgURLs = new LinkedHashSet<URI>();
         System.out.println("Name = " + ft.getName() + ", URL = " + ft.getUrl());
         try
         {
            StringBuilder threadSource = httpGetWithResponse(ft.getUrl());
            Document doc = Jsoup.parse(threadSource.toString());
            Element firstContent = doc.select("div.content").first();
            Elements imgTags = firstContent.getElementsByTag("img");
            for (Element imTag : imgTags)
            {
               String imgUrl = imTag.attr("src");
               boolean isEmoticon = (imTag.className() != null && imTag.className().contains("inlineimg"));
               if (imgUrl != null && !isEmoticon)
               {
                  // hack for photobucket originals:
                  if (imgUrl.contains("photobucket.com"))
                  {
                     imgUrl = imgUrl + "~original";
                  }
                  imgURLs.add(new URI(imgUrl));
               }
            }
            ft.setImgURLs(imgURLs);
         }
         catch (Exception e)
         {
            throw new RuntimeException(e);
         }
      }
   }

   private StringBuilder httpGetWithResponse(URI url) throws Exception
   {
      StringBuilder responseSb;
      HttpGet request = new HttpGet(url);
      CloseableHttpResponse response = httpClient.execute(request);
      try
      {
         System.out.println("----------------------------------------");
         System.out.println(response.getStatusLine());
         responseSb = new StringBuilder(EntityUtils.toString(response.getEntity()));
      }
      finally
      {
         response.close();
      }

      return responseSb;
   }

   private byte[] httpGetAsByteArray(URI url) throws Exception
   {
      byte[] responseByteArray;
      HttpGet request = new HttpGet(url);
      CloseableHttpResponse response = httpClient.execute(request);
      try
      {
         System.out.println("----------------------------------------");
         System.out.println(response.getStatusLine());
         responseByteArray = EntityUtils.toByteArray(response.getEntity());
      }
      finally
      {
         response.close();
      }

      return responseByteArray;
   }
   
   private void analyseImages(Collection<FThread> fThreads)
   {
      Iterator<FThread> iter = fThreads.iterator();
      while (iter.hasNext())
      {
         FThread ft = iter.next();
         Collection<URI> imgURLs = ft.getImgURLs();
         Iterator<URI> iter2 = imgURLs.iterator();
         Collection<GPSInfo> gpsInfos = new LinkedHashSet<GPSInfo>();
         ft.setGPSInfos(gpsInfos);
         while (iter2.hasNext())
         {
            URI url = iter2.next();
            // GPS data is always stripped from imgur and tapatalk - so don't bother with these
            if (!url.toString().contains("imgur.com") && !url.toString().contains("tapatalk"))
            {
               GPSInfo gi = analyseImage(url);
               if (gi != null)
               {
                  // bingo!
                  gpsInfos.add(gi);
               }
            }
         }
      }
   }

   private GPSInfo analyseImage(URI uri)
   {
      byte[] imgContent;
      try
      {
         imgContent = httpGetAsByteArray(uri);
      }
      catch (Exception e)
      {
         throw new RuntimeException(e);
      }
      
      javaxt.io.Image image = new javaxt.io.Image(imgContent);
      java.util.HashMap<Integer, Object> exif = image.getExifTags();
      
      if (_debug)
      {
         //Print Camera Info
         System.out.println("EXIF Fields: " + exif.size());
         System.out.println("-----------------------------");
         System.out.println("Date: " + exif.get(0x0132)); //0x9003       
         System.out.println("Camera: " + exif.get(0x0110));
         System.out.println("Manufacturer: " + exif.get(0x010F));
         System.out.println("Focal Length: " + exif.get(0x920A));
         System.out.println("F-Stop: " + exif.get(0x829D));
         System.out.println("Exposure Time (1 / Shutter Speed): " + exif.get(0x829A));
         System.out.println("ISO Speed Ratings: " + exif.get(0x8827));
         System.out.println("Shutter Speed Value (APEX): " + exif.get(0x9201));
         System.out.println("Shutter Speed (Exposure Time): " + exif.get(0x9201));
         System.out.println("Aperture Value (APEX): " + exif.get(0x9202));
      }
      
      double[] coord = image.getGPSCoordinate();
      if (coord != null)
      {
         if (_debug)
         {
            System.out.println("GPS Coordinate: " + coord[0] + ", " + coord[1]);
            System.out.println("GPS Datum: " + image.getGPSDatum());
         }
         GPSInfo g = new GPSInfo();
         g.coord = coord;
         g.gpsDatum = image.getGPSDatum();
         g.url = uri.toString();
         
         return g;
      }

      return null;
   }
   
   private URI buildForumSectionURI(int forumNum, int pageNum)
   {
      URI url = null;
      URIBuilder builder = new URIBuilder();
      builder.setScheme("http").setHost(VBF_HOST).setPort(VBF_PORT).setPath("/forumdisplay.php")
         .setParameter("f", ""+forumNum)
         .setParameter("page", ""+pageNum);
      try
      {
         url = builder.build();
      }
      catch (URISyntaxException e)
      {
         throw new RuntimeException(e);
      }
      
      return url;
   }

   private URI buildPageThreadURI(String threadId)
   {
      URI url = null;
      URIBuilder builder = new URIBuilder();
      builder.setScheme("http").setHost(VBF_HOST).setPort(VBF_PORT).setPath("/showthread.php")
         .setParameter("t", threadId);
      try
      {
         url = builder.build();
      }
      catch (URISyntaxException e)
      {
         throw new RuntimeException(e);
      }
      
      return url;
   }
   
   private void dumpUsefulInfo(Collection<FThread> fThreads)
   {
      final String NL = "\n";
      Iterator<FThread> iter = fThreads.iterator();
      StringBuilder content = new StringBuilder();
      while (iter.hasNext())
      {
         FThread ft = iter.next();
         Collection<GPSInfo> gis = ft.getGpsInfos();
         if (gis != null && !gis.isEmpty())
         {
            content.append(ft.getName()).append(NL);
            content.append(ft.getThreadId()).append(NL);
            for (GPSInfo gi : gis)
            {
               content.append("GPS Coordinate: ").append(gi.coord[0]).append(", ").append(gi.coord[1]).append(NL);
               content.append("GPS Datum: ").append(gi.gpsDatum).append(NL);
               content.append("URL: ").append(gi.url);
            }
            content.append("====================================================");
         }
      }
      
      writeStringIntoFile(content.toString(), "geo_imgs.txt");
   }

   private void dumpAllImgURLsToFile(Collection<FThread> fThreads)
   {
      Iterator<FThread> iter = fThreads.iterator();
      StringBuilder content = new StringBuilder();
      while (iter.hasNext())
      {
         FThread ft = iter.next();
         Collection<URI> imgURLs = ft.getImgURLs();
         Iterator<URI> iter2 = imgURLs.iterator();
         while(iter2.hasNext())
         {
            URI url = iter2.next();
            content.append(ft.getThreadId()).append("=").append(url).append("\n");
         }
      }
      
      writeStringIntoFile(content.toString(), "img_urls.txt");
   }

   private void writeStringIntoFile(String content, String filename)
   {
      File f = new File(filename);
      
      BufferedWriter bw = null;
      try
      {
         FileWriter fw = new FileWriter(f);
         bw = new BufferedWriter(fw);
         bw.write(content);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
      finally
      {
         try
         {
            if (bw != null)
            {
               bw.close();
            }
         }
         catch (IOException e)
         {
            throw new RuntimeException(e);
         }
      }
   }

   class GPSInfo
   {
      double[] coord;
      String gpsDatum;
      String url;
   }
   
}

 /*
 * Copyright 2004-2014 Pilz Ireland Industrial Automation Ltd. All Rights
 * Reserved. PILZ PROPRIETARY/CONFIDENTIAL.
 *
 * Created on 30 Jul 2014
 */