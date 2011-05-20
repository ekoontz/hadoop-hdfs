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
package org.apache.hadoop.hdfs.server.namenode;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspWriter;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hdfs.DFSUtil;
import org.apache.hadoop.hdfs.DFSConfigKeys;
import org.apache.hadoop.hdfs.protocol.Block;
import org.apache.hadoop.hdfs.protocol.DatanodeID;
import org.apache.hadoop.hdfs.protocol.FSConstants.UpgradeAction;
import org.apache.hadoop.hdfs.security.token.delegation.DelegationTokenIdentifier;
import org.apache.hadoop.hdfs.server.common.JspHelper;
import org.apache.hadoop.hdfs.server.common.Storage;
import org.apache.hadoop.hdfs.server.common.UpgradeStatusReport;
import org.apache.hadoop.hdfs.server.common.Storage.StorageDirectory;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.util.ServletUtil;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.util.VersionInfo;

import org.znerd.xmlenc.*;

class NamenodeJspHelper {
  static String getSafeModeText(FSNamesystem fsn) {
    if (!fsn.isInSafeMode())
      return "";
    return "<div>Safe mode is ON. <em>" + fsn.getSafeModeTip() + "</em></div>";
  }
  
  /**
   * returns security mode of the cluster (namenode)
   * @return "on" if security is on, and "off" otherwise
   */
  static String getSecurityModeText() {  
    if(UserGroupInformation.isSecurityEnabled()) {
      return "<div>Security is <em>ON</em>.</div>";
    } else {
      return "<div>Security is <em>OFF</em>.</div>";
    }
  }

  static String getInodeLimitText(FSNamesystem fsn) {
    long inodes = fsn.dir.totalInodes();
    long blocks = fsn.getBlocksTotal();
    long maxobjects = fsn.getMaxObjects();

    MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
    MemoryUsage heap = mem.getHeapMemoryUsage();
    long totalMemory = heap.getUsed();
    long maxMemory = heap.getMax();
    long commitedMemory = heap.getCommitted();
    
    MemoryUsage nonHeap = mem.getNonHeapMemoryUsage();
    long totalNonHeap = nonHeap.getUsed();
    long maxNonHeap = nonHeap.getMax();
    long commitedNonHeap = nonHeap.getCommitted();

    long used = (totalMemory * 100) / commitedMemory;
    long usedNonHeap = (totalNonHeap * 100) / commitedNonHeap;

    String str = "<div>" + inodes + " files and directories, " + blocks + " blocks = "
        + (inodes + blocks) + " total";
    if (maxobjects != 0) {
      long pct = ((inodes + blocks) * 100) / maxobjects;
      str += " / " + maxobjects + " (" + pct + "%)";
    }
    str += ".</div>";
    str += "<div>Heap Memory used " + StringUtils.byteDesc(totalMemory) + " is "
        + " " + used + "% of Commited Heap Memory " 
        + StringUtils.byteDesc(commitedMemory)
        + ".</div>"
        + "<div>Max Heap Memory is " + StringUtils.byteDesc(maxMemory) +
        ". </div>";
    str += "<div>Non Heap Memory used " + StringUtils.byteDesc(totalNonHeap) + " is"
        + " " + usedNonHeap + "% of " + " Commited Non Heap Memory "
        + StringUtils.byteDesc(commitedNonHeap) + ".</div><div>Max Non Heap Memory is "
        + StringUtils.byteDesc(maxNonHeap) + ".</div>";
    return str;
  }

  static String getUpgradeStatusText(FSNamesystem fsn) {
    String statusText = "";
    try {
      UpgradeStatusReport status = fsn
          .distributedUpgradeProgress(UpgradeAction.GET_STATUS);
      statusText = (status == null ? "There are no upgrades in progress."
          : status.getStatusText(false));
    } catch (IOException e) {
      statusText = "Upgrade status unknown.";
    }
    return statusText;
  }

  /** Return a table containing version information. */
  static String getVersionTable(FSNamesystem fsn) {
    return "<div class='dfstable'><table>"
        + "\n  <tr><th>Started</th><td>" + fsn.getStartTime() + "</td></tr>"
        + "\n  <tr><th>Version</th>"
        + "\n      <td>" + VersionInfo.getVersion() + ", " + VersionInfo.getRevision() + "</td>"
        + "\n  </tr>"
        + "\n  <tr><th>Compiled</th>"
        + "\n      <td>" + VersionInfo.getDate() + " by " + VersionInfo.getUser() + " from " + VersionInfo.getBranch() + "</td>"
        + "\n  </tr>"
        + "\n  <tr><th>Upgrades</th>"
        + "\n      <td>" + getUpgradeStatusText(fsn)  + "</td>"
        + "\n  </tr>"
        + "\n  <tr><th>Cluster ID</th>"
        + "\n      <td>" + fsn.getClusterId() + "</td>"
        + "\n </tr>"
        + "\n  <tr><th>Block Pool ID</th>"
        + "\n      <td>" + fsn.getBlockPoolId() + "</td>"
        + "\n </tr></table></div>";
  }

  static String getWarningText(FSNamesystem fsn) {
    // Ideally this should be displayed in RED
    long missingBlocks = fsn.getMissingBlocksCount();
    if (missingBlocks > 0) {
      return "<div class='warning'>"
           + "<a href='/corrupt_files.jsp' title='List corrupt files'>"
           +  "WARNING :" + " There are " + missingBlocks
           + " missing blocks. Please check the log or run fsck.</a></div>";
    }
    return "";
  }

  static class HealthJsp {
    private int rowNum = 0;
    private int colNum = 0;
    private String sorterField = null;
    private String sorterOrder = null;

    private String rowTxt() {
      colNum = 0;
      return "<tr class=\"" + (((rowNum++) % 2 == 0) ? "rowNormal" : "rowAlt")
          + "\"> ";
    }

    private String colTxt() {
      return "<td id=\"col" + ++colNum + "\"> ";
    }

    private String colTxt(String title) {
      return "<td id=\"col" + ++colNum + "\" title=\"" + title + "\"> ";
    }

    private void counterReset() {
      colNum = 0;
      rowNum = 0;
    }

    void generateConfReport(JspWriter out, NameNode nn,
        HttpServletRequest request) throws IOException {
      FSNamesystem fsn = nn.getNamesystem();
      FSImage fsImage = fsn.getFSImage();
      List<Storage.StorageDirectory> removedStorageDirs 
        = fsImage.getStorage().getRemovedStorageDirs();

      // FS Image storage configuration
      out.print("<h4> " + nn.getRole() + " Storage: </h4>");
      out.print("<div class=\"dfstable\"> <table title=\"NameNode Storage\">\n"
              + "<thead><tr><th>Storage Directory</th><th>Type</th><th>State</th></tr></thead>");

      StorageDirectory st = null;
      for (Iterator<StorageDirectory> it 
             = fsImage.getStorage().dirIterator(); it.hasNext();) {
        st = it.next();
        String dir = "" + st.getRoot();
        String type = "" + st.getStorageDirType();
        out.print("<tr><td>" + dir + "</td><td>" + type
            + "</td><td>Active</td></tr>");
      }

      long storageDirsSize = removedStorageDirs.size();
      for (int i = 0; i < storageDirsSize; i++) {
        st = removedStorageDirs.get(i);
        String dir = "" + st.getRoot();
        String type = "" + st.getStorageDirType();
        out.print("<tr><th>" + dir + "</th><td>" + type
            + "</td><td><span class='failed'>Failed</span></td></tr>");
      }

      out.print("</table></div>");

    }

    void generateHealthReport(JspWriter out, NameNode nn,
        HttpServletRequest request) throws IOException {
      FSNamesystem fsn = nn.getNamesystem();
      ArrayList<DatanodeDescriptor> live = new ArrayList<DatanodeDescriptor>();
      ArrayList<DatanodeDescriptor> dead = new ArrayList<DatanodeDescriptor>();
      fsn.DFSNodesStatus(live, dead);
      // If a data node has been first included in the include list, 
      // then decommissioned, then removed from both include and exclude list.  
      // We make the web console to "forget" this node by not displaying it.
      fsn.removeDecomNodeFromList(live);  
      fsn.removeDecomNodeFromList(dead); 

      int liveDecommissioned = 0;
      for (DatanodeDescriptor d : live) {
        liveDecommissioned += d.isDecommissioned() ? 1 : 0;
      }

      int deadDecommissioned = 0;
      for (DatanodeDescriptor d : dead) {
        deadDecommissioned += d.isDecommissioned() ? 1 : 0;
      }
      
      ArrayList<DatanodeDescriptor> decommissioning = fsn
          .getDecommissioningNodes();

      sorterField = request.getParameter("sorter/field");
      sorterOrder = request.getParameter("sorter/order");
      if (sorterField == null)
        sorterField = "name";
      if (sorterOrder == null)
        sorterOrder = "ASC";

      // Find out common suffix. Should this be before or after the sort?
      String port_suffix = null;
      if (live.size() > 0) {
        String name = live.get(0).getName();
        int idx = name.indexOf(':');
        if (idx > 0) {
          port_suffix = name.substring(idx);
        }

        for (int i = 1; port_suffix != null && i < live.size(); i++) {
          if (live.get(i).getName().endsWith(port_suffix) == false) {
            port_suffix = null;
            break;
          }
        }
      }

      counterReset();
      long[] fsnStats = fsn.getStats();
      long total = fsnStats[0];
      long remaining = fsnStats[2];
      long used = fsnStats[1];
      long nonDFS = total - remaining - used;
      nonDFS = nonDFS < 0 ? 0 : nonDFS;
      float percentUsed = DFSUtil.getPercentUsed(used, total);
      float percentRemaining = DFSUtil.getPercentRemaining(used, total);
      float median = 0;
      float max = 0;
      float min = 0;
      float dev = 0;
      
      if (live.size() > 0) {
        float totalDfsUsed = 0;
        float[] usages = new float[live.size()];
        int i = 0;
        for (DatanodeDescriptor dn : live) {
          usages[i++] = dn.getDfsUsedPercent();
          totalDfsUsed += dn.getDfsUsedPercent();
        }
        totalDfsUsed /= live.size();
        Arrays.sort(usages);
        median = usages[usages.length/2];
        max = usages[usages.length - 1];
        min = usages[0];
        
        for (i = 0; i < usages.length; i++) {
          dev += (usages[i] - totalDfsUsed) * (usages[i] - totalDfsUsed);
        }
        dev = (float) Math.sqrt(dev/usages.length);
      }

      long bpUsed = fsnStats[6];
      float percentBpUsed = DFSUtil.getPercentUsed(bpUsed, total);
      
      out.print("<div class=\"dfstable stats\"> <table>"
          + "\n<tr><th>Configured Capacity</th>"
          + "\n    <td>" + StringUtils.byteDesc(total) + "</td></tr>"
          + "\n<tr><th>DFS Used</th>"
          + "\n    <td>" + StringUtils.byteDesc(used) + "</td></tr>"
          + "\n<tr><th>Non DFS Used</th>"
          + "\n    <td>" + StringUtils.byteDesc(nonDFS) + "</td></tr>"
          + "\n<tr><th>DFS Remaining</th>"
          + "\n    <td>" + StringUtils.byteDesc(remaining) + "</td></tr>"
          + "\n<tr><th>DFS Used %</th>"
          + "\n    <td>" + StringUtils.limitDecimalTo2(percentUsed) + " %</td></tr>"
          + "\n<tr><th>DFS Remaining %</th>"
          + "\n    <td>" + StringUtils.limitDecimalTo2(percentRemaining) + " %</td></tr>"
          + "\n<tr><th>Block Pool Used</th>"
          + "\n    <td>" + StringUtils.byteDesc(bpUsed) + rowTxt() + "</td></tr>"
          + "\n<tr><th>Block Pool Used %</th>"
          + "\n    <td>"+ StringUtils.limitDecimalTo2(percentBpUsed) + " %</td></tr>"
          + "\n<tr><th>DataNodes usages</th></tr>"
          + "\n<tr><th>Min %</th><th>" + "Median %" + "</th><th>" + "Max %" + "</th><th>" + "Std Dev %" + "</th></tr>"
          + "\n<tr><td>" + StringUtils.limitDecimalTo2(min) +    " %</td>"
          + "\n         <td>" + StringUtils.limitDecimalTo2(median) + " %</td>"
          + "\n         <td>" + StringUtils.limitDecimalTo2(max) +    " %</td>"
          + "\n         <td>" + StringUtils.limitDecimalTo2(dev) +    " %</td></tr>"
          + "\n<tr><th><a href=\"dfsnodelist.jsp?whatNodes=LIVE\">Live Nodes</a></th>"
          + "\n    <td>" + live.size() + " (Decommissioned: " + liveDecommissioned + ")</td></tr>"
          + "\n<tr><th><a href=\"dfsnodelist.jsp?whatNodes=DEAD\">Dead Nodes</a></th>"
          + "\n    <td>" + dead.size() + " (Decommissioned: " + deadDecommissioned + ")</td></tr>"
          + "\n    <th><a href=\"dfsnodelist.jsp?whatNodes=DECOMMISSIONING\">Decommissioning Nodes</a></th>"
          + "\n    <td>" + decommissioning.size() + "</td></tr>"
          + "\n<tr><th>Under-Replicated Blocks<br/> (Excluding missing blocks)</th>"
          + "\n    <td>" + fsn.getUnderReplicatedNotMissingBlocks() + "</td></tr>"
          + "\n</table></div>\n");

      if (live.isEmpty() && dead.isEmpty()) {
        out.print("There are no datanodes in the cluster.");
      }
    }
  }

  static String getDelegationToken(final NameNode nn,
      HttpServletRequest request, Configuration conf,
      final UserGroupInformation ugi) throws IOException, InterruptedException {
    Token<DelegationTokenIdentifier> token = ugi
        .doAs(new PrivilegedExceptionAction<Token<DelegationTokenIdentifier>>() {
          public Token<DelegationTokenIdentifier> run() throws IOException {
            return nn.getDelegationToken(new Text(ugi.getUserName()));
          }
        });
    return token == null ? null : token.encodeToUrlString();
  }

  static void redirectToRandomDataNode(ServletContext context,
      HttpServletRequest request, HttpServletResponse resp) throws IOException,
      InterruptedException {
    final NameNode nn = (NameNode) context.getAttribute("name.node");
    final Configuration conf = (Configuration) context
        .getAttribute(JspHelper.CURRENT_CONF);
    final DatanodeID datanode = nn.getNamesystem().getRandomDatanode();
    UserGroupInformation ugi = JspHelper.getUGI(context, request, conf);
    String tokenString = getDelegationToken(nn, request, conf, ugi);
    // if the user is defined, get a delegation token and stringify it
    final String redirectLocation;
    final String nodeToRedirect;
    int redirectPort;
    if (datanode != null) {
      nodeToRedirect = datanode.getHost();
      redirectPort = datanode.getInfoPort();
    } else {
      nodeToRedirect = nn.getHttpAddress().getHostName();
      redirectPort = nn.getHttpAddress().getPort();
    }
    String addr = NameNode.getHostPortString(nn.getNameNodeAddress());
    String fqdn = InetAddress.getByName(nodeToRedirect).getCanonicalHostName();
    redirectLocation = "http://" + fqdn + ":" + redirectPort
        + "/browseDirectory.jsp?namenodeInfoPort="
        + nn.getHttpAddress().getPort() + "&dir=/"
        + (tokenString == null ? "" :
           JspHelper.getDelegationTokenUrlParam(tokenString))
        + JspHelper.getUrlParam(JspHelper.NAMENODE_ADDRESS, addr);
    resp.sendRedirect(redirectLocation);
  }

  static class NodeListJsp {
    private int rowNum = 0;

    private long diskBytes = 1024 * 1024 * 1024;
    private String diskByteStr = "GB";

    private String sorterField = null;
    private String sorterOrder = null;

    private String whatNodes = "LIVE";

    private String rowTxt() {
      return "<tr class=\"" + (((rowNum++) % 2 == 0) ? "rowNormal" : "rowAlt")
          + "\"> ";
    }

    private void counterReset() {
      rowNum = 0;
    }

    private String nodeHeaderStr(String name) {
      String ret = "class=header";
      String order = "ASC";
      if (name.equals(sorterField)) {
        ret += sorterOrder;
        if (sorterOrder.equals("ASC"))
          order = "DSC";
      }
      ret += " onClick=\"window.document.location="
          + "'/dfsnodelist.jsp?whatNodes=" + whatNodes + "&sorter/field="
          + name + "&sorter/order=" + order
          + "'\" title=\"sort on this column\"";

      return ret;
    }

    private void generateNodeDataHeader(JspWriter out, DatanodeDescriptor d,
        String suffix, boolean alive, int nnHttpPort, String nnaddr)
        throws IOException {
      // from nn_browsedfscontent.jsp:
      String url = "http://" + d.getHostName() + ":" + d.getInfoPort()
          + "/browseDirectory.jsp?namenodeInfoPort=" + nnHttpPort + "&dir="
          + URLEncoder.encode("/", "UTF-8")
          + JspHelper.getUrlParam(JspHelper.NAMENODE_ADDRESS, nnaddr);

      String name = d.getHostName() + ":" + d.getPort();
      if (!name.matches("\\d+\\.\\d+.\\d+\\.\\d+.*"))
        name = name.replaceAll("\\.[^.:]*", "");
      int idx = (suffix != null && name.endsWith(suffix)) ? name
          .indexOf(suffix) : -1;

      out.print(rowTxt() + "<td class=\"name\"><a title=\"" + d.getHost() + ":"
          + d.getPort() + "\" href=\"" + url + "\">"
          + ((idx > 0) ? name.substring(0, idx) : name) + "</a>"
          + ((alive) ? "" : "\n"));
    }

    void generateDecommissioningNodeData(JspWriter out, DatanodeDescriptor d,
        String suffix, boolean alive, int nnHttpPort, String nnaddr)
        throws IOException {
      generateNodeDataHeader(out, d, suffix, alive, nnHttpPort, nnaddr);
      if (!alive) {
        return;
      }

      long decommRequestTime = d.decommissioningStatus.getStartTime();
      long timestamp = d.getLastUpdate();
      long currentTime = System.currentTimeMillis();
      long hoursSinceDecommStarted = (currentTime - decommRequestTime)/3600000;
      long remainderMinutes = ((currentTime - decommRequestTime)/60000) % 60;
      out.print("<td class=\"lastcontact\"> "
          + ((currentTime - timestamp) / 1000)
          + "<td class=\"underreplicatedblocks\">"
          + d.decommissioningStatus.getUnderReplicatedBlocks()
          + "<td class=\"blockswithonlydecommissioningreplicas\">"
          + d.decommissioningStatus.getDecommissionOnlyReplicas() 
          + "<td class=\"underrepblocksinfilesunderconstruction\">"
          + d.decommissioningStatus.getUnderReplicatedInOpenFiles()
          + "<td class=\"timesincedecommissionrequest\">"
          + hoursSinceDecommStarted + " hrs " + remainderMinutes + " mins"
          + "\n");
    }
    
    void generateNodeData(JspWriter out, DatanodeDescriptor d, String suffix,
        boolean alive, int nnHttpPort, String nnaddr) throws IOException {
      /*
       * Say the datanode is dn1.hadoop.apache.org with ip 192.168.0.5 we use:
       * 1) d.getHostName():d.getPort() to display. Domain and port are stripped
       *    if they are common across the nodes. i.e. "dn1"
       * 2) d.getHost():d.Port() for "title". i.e. "192.168.0.5:50010"
       * 3) d.getHostName():d.getInfoPort() for url.
       *    i.e. "http://dn1.hadoop.apache.org:50075/..."
       * Note that "d.getHost():d.getPort()" is what DFS clients use to
       * interact with datanodes.
       */

      generateNodeDataHeader(out, d, suffix, alive, nnHttpPort, nnaddr);
      if (!alive) {
        out.print("<td class=\"decommissioned\"> " + 
            d.isDecommissioned() + "\n");
        return;
      }

      long c = d.getCapacity();
      long u = d.getDfsUsed();
      long nu = d.getNonDfsUsed();
      long r = d.getRemaining();
      String percentUsed = StringUtils.limitDecimalTo2(d.getDfsUsedPercent());
      String percentRemaining = StringUtils.limitDecimalTo2(d
          .getRemainingPercent());

      String adminState = d.getAdminState().toString();

      long timestamp = d.getLastUpdate();
      long currentTime = System.currentTimeMillis();
      
      long bpUsed = d.getBlockPoolUsed();
      String percentBpUsed = StringUtils.limitDecimalTo2(d
          .getBlockPoolUsedPercent());

      out.print("<td class=\"lastcontact\"> "
          + ((currentTime - timestamp) / 1000)
          + "<td class=\"adminstate\">"
          + adminState
          + "<td align=\"right\" class=\"capacity\">"
          + StringUtils.limitDecimalTo2(c * 1.0 / diskBytes)
          + "<td align=\"right\" class=\"used\">"
          + StringUtils.limitDecimalTo2(u * 1.0 / diskBytes)
          + "<td align=\"right\" class=\"nondfsused\">"
          + StringUtils.limitDecimalTo2(nu * 1.0 / diskBytes)
          + "<td align=\"right\" class=\"remaining\">"
          + StringUtils.limitDecimalTo2(r * 1.0 / diskBytes)
          + "<td align=\"right\" class=\"pcused\">"
          + percentUsed
          + "<td class=\"pcused\">"
          + ServletUtil.percentageGraph((int) Double.parseDouble(percentUsed),
              100) 
          + "<td align=\"right\" class=\"pcremaining`\">"
          + percentRemaining 
          + "<td title=" + "\"blocks scheduled : "
          + d.getBlocksScheduled() + "\" class=\"blocks\">" + d.numBlocks()+"\n"
          + "<td align=\"right\" class=\"bpused\">"
          + StringUtils.limitDecimalTo2(bpUsed * 1.0 / diskBytes)
          + "<td align=\"right\" class=\"pcbpused\">"
          + percentBpUsed
          + "<td align=\"right\" class=\"volfails\">"
          + d.getVolumeFailures() + "\n");
    }

    void generateNodesList(ServletContext context, JspWriter out,
        HttpServletRequest request) throws IOException {
      ArrayList<DatanodeDescriptor> live = new ArrayList<DatanodeDescriptor>();
      ArrayList<DatanodeDescriptor> dead = new ArrayList<DatanodeDescriptor>();
      final NameNode nn = (NameNode)context.getAttribute("name.node");
      nn.getNamesystem().DFSNodesStatus(live, dead);
      nn.getNamesystem().removeDecomNodeFromList(live);
      nn.getNamesystem().removeDecomNodeFromList(dead);
      InetSocketAddress nnSocketAddress = (InetSocketAddress) context
          .getAttribute(NameNode.NAMENODE_ADDRESS_ATTRIBUTE_KEY);
      String nnaddr = nnSocketAddress.getAddress().getHostAddress() + ":"
          + nnSocketAddress.getPort();

      whatNodes = request.getParameter("whatNodes"); // show only live or only
                                                     // dead nodes
      sorterField = request.getParameter("sorter/field");
      sorterOrder = request.getParameter("sorter/order");
      if (sorterField == null)
        sorterField = "name";
      if (sorterOrder == null)
        sorterOrder = "ASC";

      JspHelper.sortNodeList(live, sorterField, sorterOrder);

      // Find out common suffix. Should this be before or after the sort?
      String port_suffix = null;
      if (live.size() > 0) {
        String name = live.get(0).getName();
        int idx = name.indexOf(':');
        if (idx > 0) {
          port_suffix = name.substring(idx);
        }

        for (int i = 1; port_suffix != null && i < live.size(); i++) {
          if (live.get(i).getName().endsWith(port_suffix) == false) {
            port_suffix = null;
            break;
          }
        }
      }

      counterReset();

      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
      }

      if (live.isEmpty() && dead.isEmpty()) {
        out.print("There are no datanodes in the cluster");
      } else {

        int nnHttpPort = nn.getHttpAddress().getPort();
        out.print("<div id=\"dfsnodetable\"> ");
        if (whatNodes.equals("LIVE")) {
          out.print("<a name=\"LiveNodes\" id=\"title\">" + "Live Datanodes : "
              + live.size() + "</a>"
              + "<br><br>\n<table border=1 cellspacing=0>\n");

          counterReset();

          if (live.size() > 0) {
            if (live.get(0).getCapacity() > 1024 * diskBytes) {
              diskBytes *= 1024;
              diskByteStr = "TB";
            }

            out.print("<tr class=\"headerRow\"> <th " + nodeHeaderStr("name")
                + "> Node <th " + nodeHeaderStr("lastcontact")
                + "> Last <br>Contact <th " + nodeHeaderStr("adminstate")
                + "> Admin State <th " + nodeHeaderStr("capacity")
                + "> Configured <br>Capacity (" + diskByteStr + ") <th "
                + nodeHeaderStr("used") + "> Used <br>(" + diskByteStr
                + ") <th " + nodeHeaderStr("nondfsused")
                + "> Non DFS <br>Used (" + diskByteStr + ") <th "
                + nodeHeaderStr("remaining") + "> Remaining <br>("
                + diskByteStr + ") <th " + nodeHeaderStr("pcused")
                + "> Used <br>(%) <th " + nodeHeaderStr("pcused")
                + "> Used <br>(%) <th " + nodeHeaderStr("pcremaining")
                + "> Remaining <br>(%) <th " + nodeHeaderStr("blocks")
                + "> Blocks <th "
                + nodeHeaderStr("bpused") + "> Block Pool<br>Used (" 
                + diskByteStr + ") <th "
                + nodeHeaderStr("pcbpused")
                + "> Block Pool<br>Used (%)"
                + "> Blocks <th " + nodeHeaderStr("volfails")
                +"> Failed Volumes\n");

            JspHelper.sortNodeList(live, sorterField, sorterOrder);
            for (int i = 0; i < live.size(); i++) {
              generateNodeData(out, live.get(i), port_suffix, true, nnHttpPort,
                  nnaddr);
            }
          }
          out.print("</table>\n");
        } else if (whatNodes.equals("DEAD")) {

          out.print("<br> <a name=\"DeadNodes\" id=\"title\"> "
              + " Dead Datanodes : " + dead.size() + "</a><br><br>\n");

          if (dead.size() > 0) {
            out.print("<table border=1 cellspacing=0> <tr id=\"row1\"> "
                + "<th " + nodeHeaderStr("node")
                + "> Node <th " + nodeHeaderStr("decommissioned")
                + "> Decommissioned\n");

            JspHelper.sortNodeList(dead, sorterField, sorterOrder);
            for (int i = 0; i < dead.size(); i++) {
              generateNodeData(out, dead.get(i), port_suffix, false,
                  nnHttpPort, nnaddr);
            }

            out.print("</table>\n");
          }
        } else if (whatNodes.equals("DECOMMISSIONING")) {
          // Decommissioning Nodes
          ArrayList<DatanodeDescriptor> decommissioning = nn.getNamesystem()
              .getDecommissioningNodes();
          out.print("<br> <a name=\"DecommissioningNodes\" id=\"title\"> "
              + " Decommissioning Datanodes : " + decommissioning.size()
              + "</a><br><br>\n");
          if (decommissioning.size() > 0) {
            out.print("<table border=1 cellspacing=0> <tr class=\"headRow\"> "
                + "<th " + nodeHeaderStr("name") 
                + "> Node <th " + nodeHeaderStr("lastcontact")
                + "> Last <br>Contact <th "
                + nodeHeaderStr("underreplicatedblocks")
                + "> Under Replicated Blocks <th "
                + nodeHeaderStr("blockswithonlydecommissioningreplicas")
                + "> Blocks With No <br> Live Replicas <th "
                + nodeHeaderStr("underrepblocksinfilesunderconstruction")
                + "> Under Replicated Blocks <br> In Files Under Construction" 
                + " <th " + nodeHeaderStr("timesincedecommissionrequest")
                + "> Time Since Decommissioning Started"
                );

            JspHelper.sortNodeList(decommissioning, "name", "ASC");
            for (int i = 0; i < decommissioning.size(); i++) {
              generateDecommissioningNodeData(out, decommissioning.get(i),
                  port_suffix, true, nnHttpPort, nnaddr);
            }
            out.print("</table>\n");
          }
        }
        out.print("</div>");
      }
    }
  }
  
  // utility class used in block_info_xml.jsp
  static class XMLBlockInfo {
    final Block block;
    final INodeFile inode;
    final FSNamesystem fsn;
    
    public XMLBlockInfo(FSNamesystem fsn, Long blockId) {
      this.fsn = fsn;
      if (blockId == null) {
        this.block = null;
        this.inode = null;
      } else {
        this.block = new Block(blockId);
        this.inode = fsn.blockManager.getINode(block);
      }
    }

    public void toXML(XMLOutputter doc) throws IOException {
      doc.startTag("block_info");
      if (block == null) {
        doc.startTag("error");
        doc.pcdata("blockId must be a Long");
        doc.endTag();
      }else{
        doc.startTag("block_id");
        doc.pcdata(""+block.getBlockId());
        doc.endTag();

        doc.startTag("block_name");
        doc.pcdata(block.getBlockName());
        doc.endTag();

        if (inode != null) {
          doc.startTag("file");

          doc.startTag("local_name");
          doc.pcdata(inode.getLocalName());
          doc.endTag();

          doc.startTag("local_directory");
          doc.pcdata(inode.getLocalParentDir());
          doc.endTag();

          doc.startTag("user_name");
          doc.pcdata(inode.getUserName());
          doc.endTag();

          doc.startTag("group_name");
          doc.pcdata(inode.getGroupName());
          doc.endTag();

          doc.startTag("is_directory");
          doc.pcdata(""+inode.isDirectory());
          doc.endTag();

          doc.startTag("access_time");
          doc.pcdata(""+inode.getAccessTime());
          doc.endTag();

          doc.startTag("is_under_construction");
          doc.pcdata(""+inode.isUnderConstruction());
          doc.endTag();

          doc.startTag("ds_quota");
          doc.pcdata(""+inode.getDsQuota());
          doc.endTag();

          doc.startTag("permission_status");
          doc.pcdata(inode.getPermissionStatus().toString());
          doc.endTag();

          doc.startTag("replication");
          doc.pcdata(""+inode.getReplication());
          doc.endTag();

          doc.startTag("disk_space_consumed");
          doc.pcdata(""+inode.diskspaceConsumed());
          doc.endTag();

          doc.startTag("preferred_block_size");
          doc.pcdata(""+inode.getPreferredBlockSize());
          doc.endTag();

          doc.endTag(); // </file>
        } 

        doc.startTag("replicas");
       
        if (fsn.blockManager.blocksMap.contains(block)) {
          Iterator<DatanodeDescriptor> it =
            fsn.blockManager.blocksMap.nodeIterator(block);

          while (it.hasNext()) {
            doc.startTag("replica");

            DatanodeDescriptor dd = it.next();

            doc.startTag("host_name");
            doc.pcdata(dd.getHostName());
            doc.endTag();

            boolean isCorrupt = fsn.getCorruptReplicaBlockIds(0,
                                  block.getBlockId()) != null;
            
            doc.startTag("is_corrupt");
            doc.pcdata(""+isCorrupt);
            doc.endTag();
            
            doc.endTag(); // </replica>
          }

        } 
        doc.endTag(); // </replicas>
                
      }
      
      doc.endTag(); // </block_info>
      
    }
  }
  
  // utility class used in corrupt_replicas_xml.jsp
  static class XMLCorruptBlockInfo {
    final FSNamesystem fsn;
    final Configuration conf;
    final Long startingBlockId;
    final int numCorruptBlocks;
    
    public XMLCorruptBlockInfo(FSNamesystem fsn, Configuration conf,
                               int numCorruptBlocks, Long startingBlockId) {
      this.fsn = fsn;
      this.conf = conf;
      this.numCorruptBlocks = numCorruptBlocks;
      this.startingBlockId = startingBlockId;
    }


    public void toXML(XMLOutputter doc) throws IOException {
      
      doc.startTag("corrupt_block_info");
      
      if (numCorruptBlocks < 0 || numCorruptBlocks > 100) {
        doc.startTag("error");
        doc.pcdata("numCorruptBlocks must be >= 0 and <= 100");
        doc.endTag();
      }
      
      doc.startTag(DFSConfigKeys.DFS_REPLICATION_KEY);
      doc.pcdata(""+conf.getInt(DFSConfigKeys.DFS_REPLICATION_KEY, 
                                DFSConfigKeys.DFS_REPLICATION_DEFAULT));
      doc.endTag();
      
      doc.startTag("num_missing_blocks");
      doc.pcdata(""+fsn.getMissingBlocksCount());
      doc.endTag();
      
      doc.startTag("num_corrupt_replica_blocks");
      doc.pcdata(""+fsn.getCorruptReplicaBlocks());
      doc.endTag();
     
      doc.startTag("corrupt_replica_block_ids");
      long[] corruptBlockIds
        = fsn.getCorruptReplicaBlockIds(numCorruptBlocks,
                                        startingBlockId);
      if (corruptBlockIds != null) {
        for (Long blockId: corruptBlockIds) {
          doc.startTag("block_id");
          doc.pcdata(""+blockId);
          doc.endTag();
        }
      }
      
      doc.endTag(); // </corrupt_replica_block_ids>

      doc.endTag(); // </corrupt_block_info>
      
      doc.getWriter().flush();
    }
  }    
}
