<%
/*
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
%>
<%@ page
  contentType="text/html; charset=UTF-8"
  import="org.apache.hadoop.util.ServletUtil"
%>
<%!
  //for java.io.Serializable
  private static final long serialVersionUID = 1L;
%>
<%
  final NamenodeJspHelper.HealthJsp healthjsp  = new NamenodeJspHelper.HealthJsp();
  NameNode nn = (NameNode)application.getAttribute("name.node");
  FSNamesystem fsn = nn.getNamesystem();
  String namenodeRole = nn.getRole().toString();
  String namenodeLabel = nn.getNameNodeAddress().getHostName() + ":" + nn.getNameNodeAddress().getPort();
%>

<html>
<head>
        <link rel="stylesheet" type="text/css" href="/static/hadoop.css">
        <title>Hadoop <%=namenodeRole%> <%=namenodeLabel%></title>
</head>
<body>
  <h1><%=namenodeRole%> '<%=namenodeLabel%>'</h1>
  <div class="report">
    <h3>Namenode Summary</h3>
    <%= NamenodeJspHelper.getVersionTable(fsn) %>

    <ul>
      <li><a href="/nn_browsedfscontent.jsp">Browse the filesystem</a></li>
      <li><a href="/logs/"><%=namenodeRole%> Logs</a></li>
    </ul>
  </div>

  <div class="report">
    <h3>Cluster Summary</h3>
    <div class="dfstable stats">

      <table>
        <tr>
          <th>Security</th>
          <td> <%= NamenodeJspHelper.getSecurityModeText() %></td>
        </tr>
        <tr>
          <th>Safe Mode</th>
          <td> <%= NamenodeJspHelper.getSafeModeText(fsn) %> </td>
        </tr>
        <tr>
          <th>Capacity</th>
          <td> <%= NamenodeJspHelper.getInodeLimitText(fsn)%> </td>
        </tr>
        <tr>
          <th>Corrupt files</th>
          <td>
            <a class="warning" href="/corrupt_files.jsp" title="List corrupt files">
              <%= NamenodeJspHelper.getWarningText(fsn) %>
            </a>
          </td>
        </tr>
      </table>

    </div>

  </div>
  
  <div class="report">
    <h3>Health</h3>
    <% healthjsp.generateHealthReport(out, nn, request); %>
  </div>

  <div class="report">
    <h3>Usage</h3>
    <% healthjsp.generateUsageReport(out, nn, request); %>
  </div>


  <div class="report">
    <h3>Configuration</h3>
    <% healthjsp.generateConfReport(out, nn, request); %>
  </div>

<%
out.println(ServletUtil.htmlFooter());
%>
