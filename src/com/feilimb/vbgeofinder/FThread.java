package com.feilimb.vbgeofinder;

import java.net.URI;
import java.util.Collection;

import com.feilimb.vbgeofinder.VBGeoFinder.GPSInfo;

public class FThread
{
   private URI _url;
   
   private String _name;

   private String _threadId;
   
   private Collection<URI> _imgURLs;

   private Collection<GPSInfo> _gpsInfos;
   
   public FThread(String name, URI url, String threadId)
   {
      this._name = name;
      this._url = url;
      this._threadId = threadId;
   }
   
   public URI getUrl()
   {
      return _url;
   }
   
   public void setUrl(URI url)
   {
      this._url = url;
   }
   
   public String getName()
   {
      return _name;
   }

   public void setName(String _name)
   {
      this._name = _name;
   }

   public void setImgURLs(Collection<URI> imgURLs)
   {
      this._imgURLs = imgURLs;
   }
   
   public Collection<URI> getImgURLs()
   {
      return this._imgURLs;
   }
   
   public String getThreadId()
   {
      return _threadId;
   }

   public void setGPSInfos(Collection<GPSInfo> gpsInfos)
   {
      this._gpsInfos = gpsInfos;
   }
   
   public Collection<GPSInfo> getGpsInfos()
   {
      return _gpsInfos;
   }
}

 /*
 * Copyright 2004-2014 Pilz Ireland Industrial Automation Ltd. All Rights
 * Reserved. PILZ PROPRIETARY/CONFIDENTIAL.
 *
 * Created on 30 Jul 2014
 */