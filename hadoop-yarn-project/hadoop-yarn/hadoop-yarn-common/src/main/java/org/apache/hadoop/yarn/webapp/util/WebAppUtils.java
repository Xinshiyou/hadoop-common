/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.hadoop.yarn.webapp.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.classification.InterfaceAudience.Private;
import org.apache.hadoop.classification.InterfaceStability.Evolving;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.http.HttpConfig;
import org.apache.hadoop.http.HttpConfig.Policy;
import org.apache.hadoop.net.NetUtils;
import org.apache.hadoop.yarn.conf.HAUtil;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.util.RMHAUtils;

import com.google.common.base.Joiner;

@Private
@Evolving
public class WebAppUtils {
  private static final Joiner JOINER = Joiner.on("");

  public static void setRMWebAppPort(Configuration conf, int port) {
    String hostname = getRMWebAppURLWithoutScheme(conf);
    hostname =
        (hostname.contains(":")) ? hostname.substring(0, hostname.indexOf(":"))
            : hostname;
    setRMWebAppHostnameAndPort(conf, hostname, port);
  }

  public static void setRMWebAppHostnameAndPort(Configuration conf,
      String hostname, int port) {
    String resolvedAddress = hostname + ":" + port;
    if (HttpConfig.isSecure()) {
      conf.set(YarnConfiguration.RM_WEBAPP_HTTPS_ADDRESS, resolvedAddress);
    } else {
      conf.set(YarnConfiguration.RM_WEBAPP_ADDRESS, resolvedAddress);
    }
  }
  
  public static void setNMWebAppHostNameAndPort(Configuration conf,
      String hostName, int port) {
    if (HttpConfig.isSecure()) {
      conf.set(YarnConfiguration.NM_WEBAPP_HTTPS_ADDRESS,
          hostName + ":" + port);
    } else {
      conf.set(YarnConfiguration.NM_WEBAPP_ADDRESS,
          hostName + ":" + port);
    }
  }
  
  public static String getRMWebAppURLWithScheme(Configuration conf) {
    return JOINER.join(HttpConfig.getSchemePrefix(),
        HttpConfig.isSecure() ? conf.get(
            YarnConfiguration.RM_WEBAPP_HTTPS_ADDRESS,
            YarnConfiguration.DEFAULT_RM_WEBAPP_HTTPS_ADDRESS) : conf.get(
            YarnConfiguration.RM_WEBAPP_ADDRESS,
            YarnConfiguration.DEFAULT_RM_WEBAPP_ADDRESS));
  }
  
  public static String getRMWebAppURLWithoutScheme(Configuration conf) {
    if (HttpConfig.isSecure()) {
      return conf.get(YarnConfiguration.RM_WEBAPP_HTTPS_ADDRESS,
          YarnConfiguration.DEFAULT_RM_WEBAPP_HTTPS_ADDRESS);
    }else {
      return conf.get(YarnConfiguration.RM_WEBAPP_ADDRESS,
          YarnConfiguration.DEFAULT_RM_WEBAPP_ADDRESS);
    }
  }

  public static List<String> getProxyHostsAndPortsForAmFilter(
      Configuration conf) {
    List<String> addrs = new ArrayList<String>();
    String proxyAddr = conf.get(YarnConfiguration.PROXY_ADDRESS);
    // If PROXY_ADDRESS isn't set, fallback to RM_WEBAPP(_HTTPS)_ADDRESS
    // There could be multiple if using RM HA
    if (proxyAddr == null || proxyAddr.isEmpty()) {
      // If RM HA is enabled, try getting those addresses
      if (HAUtil.isHAEnabled(conf)) {
        List<String> haAddrs =
            RMHAUtils.getRMHAWebappAddresses(new YarnConfiguration(conf));
        for (String addr : haAddrs) {
          try {
            InetSocketAddress socketAddr = NetUtils.createSocketAddr(addr);
            addrs.add(getResolvedAddress(socketAddr));
          } catch(IllegalArgumentException e) {
            // skip if can't resolve
          }
        }
      }
      // If couldn't resolve any of the addresses or not using RM HA, fallback
      if (addrs.isEmpty()) {
        addrs.add(getResolvedRMWebAppURLWithoutScheme(conf));
      }
    } else {
      addrs.add(proxyAddr);
    }
    return addrs;
  }
  
  public static String getProxyHostAndPort(Configuration conf) {
    String addr = conf.get(YarnConfiguration.PROXY_ADDRESS);
    if(addr == null || addr.isEmpty()) {
      addr = getResolvedRMWebAppURLWithoutScheme(conf);
    }
    return addr;
  }

  public static String getResolvedRMWebAppURLWithScheme(Configuration conf) {
    return HttpConfig.getSchemePrefix()
        + getResolvedRMWebAppURLWithoutScheme(conf);
  }
  
  public static String getResolvedRMWebAppURLWithoutScheme(Configuration conf) {
    return getResolvedRMWebAppURLWithoutScheme(conf,
        HttpConfig.isSecure() ? Policy.HTTPS_ONLY : Policy.HTTP_ONLY);
  }
  
  public static String getResolvedRMWebAppURLWithoutScheme(Configuration conf,
      Policy httpPolicy) {
    InetSocketAddress address = null;
    if (httpPolicy == Policy.HTTPS_ONLY) {
      address =
          conf.getSocketAddr(YarnConfiguration.RM_WEBAPP_HTTPS_ADDRESS,
              YarnConfiguration.DEFAULT_RM_WEBAPP_HTTPS_ADDRESS,
              YarnConfiguration.DEFAULT_RM_WEBAPP_HTTPS_PORT);
    } else {
      address =
          conf.getSocketAddr(YarnConfiguration.RM_WEBAPP_ADDRESS,
              YarnConfiguration.DEFAULT_RM_WEBAPP_ADDRESS,
              YarnConfiguration.DEFAULT_RM_WEBAPP_PORT);      
    }
    return getResolvedAddress(address);
  }

  private static String getResolvedAddress(InetSocketAddress address) {
    address = NetUtils.getConnectAddress(address);
    StringBuilder sb = new StringBuilder();
    InetAddress resolved = address.getAddress();
    if (resolved == null || resolved.isAnyLocalAddress() ||
        resolved.isLoopbackAddress()) {
      String lh = address.getHostName();
      try {
        lh = InetAddress.getLocalHost().getCanonicalHostName();
      } catch (UnknownHostException e) {
        //Ignore and fallback.
      }
      sb.append(lh);
    } else {
      sb.append(address.getHostName());
    }
    sb.append(":").append(address.getPort());
    return sb.toString();
  }
  
  public static String getNMWebAppURLWithoutScheme(Configuration conf) {
    if (HttpConfig.isSecure()) {
      return conf.get(YarnConfiguration.NM_WEBAPP_HTTPS_ADDRESS,
        YarnConfiguration.DEFAULT_NM_WEBAPP_HTTPS_ADDRESS);
    } else {
      return conf.get(YarnConfiguration.NM_WEBAPP_ADDRESS,
        YarnConfiguration.DEFAULT_NM_WEBAPP_ADDRESS);
    }
  }
  
  /**
   * if url has scheme then it will be returned as it is else it will return
   * url with scheme.
   * @param schemePrefix eg. http:// or https://
   * @param url
   * @return url with scheme
   */
  public static String getURLWithScheme(String schemePrefix, String url) {
    // If scheme is provided then it will be returned as it is
    if (url.indexOf("://") > 0) {
      return url;
    } else {
      return schemePrefix + url;
    }
  }
}
