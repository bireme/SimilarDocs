/*=========================================================================

    Copyright © 2016 BIREME/PAHO/WHO

    This file is part of SimilarDocs.

    SimilarDocs is free software: you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public License as
    published by the Free Software Foundation, either version 2.1 of
    the License, or (at your option) any later version.

    SimilarDocs is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with SimilarDocs. If not, see <http://www.gnu.org/licenses/>.

=========================================================================*/

package org.bireme.sds;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.bireme.sd.SimDocsSearch;
import org.bireme.sd.service.Conf;
import org.bireme.sd.service.UpdaterService;
import org.bireme.sd.service.TopIndex;

import scala.collection.mutable.HashSet;
import scala.collection.mutable.Set;

/**
 *
 * @author Heitor Barbieri
 * date 20161024
 */
public class SDService extends HttpServlet {
    private TopIndex topIndex;
    private UpdaterService updaterService;
    private SimDocsSearch simSearch;
    
    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        final ServletContext context = config.getServletContext();

        final String sdIndexPath = context.getInitParameter("SD_INDEX_PATH");
        if (sdIndexPath == null) throw new ServletException(
                                              "empty 'SD_INDEX_PATH' config");
        final String topIndexPath = context.getInitParameter("TOP_INDEX_PATH");
        if (topIndexPath == null) throw new ServletException(
                                              "empty 'TOP_INDEX_PATH' config");
        final String decsIndexPath = context.getInitParameter("DECS_INDEX_PATH");
        if (decsIndexPath == null) throw new ServletException(
                                              "empty 'DECS_INDEX_PATH' config");

        Tools.deleteLockFile(sdIndexPath);
        Tools.deleteLockFile(topIndexPath);
        Tools.deleteLockFile(decsIndexPath);

        simSearch = new SimDocsSearch(sdIndexPath, decsIndexPath);
        topIndex = new TopIndex(simSearch, topIndexPath, Conf.idxFldNames());
        updaterService = new UpdaterService(topIndex);

        context.setAttribute("MAINTENANCE_MODE", Boolean.FALSE);
        //System.out.println("I will call 'updaterService.start()'");
        //updaterService.start(); // Demora muita para finalizar, deixa para atualização do índice
        //System.out.println("After call of 'updaterService.start()'");
    }
    
    @Override
    public void destroy() {
        topIndex.close();
        simSearch.close();
        updaterService.stop();
        super.destroy();
    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected void processRequest(final HttpServletRequest request,
                                  final HttpServletResponse response)
                                                        throws ServletException,
                                                                   IOException {

        /*
        System.out.println("=========== HEADER =============================");
        final java.util.Enumeration<String> names = request.getHeaderNames();
        while(names.hasMoreElements()) {
            final String name = names.nextElement();
            System.out.println("[" + name + "]: [" + request.getHeader(name) + "]");              
        }
        final Map<String,String[]> paramMap = request.getParameterMap();
        for (Map.Entry<String,String[]> elem : paramMap.entrySet()) {
          
          System.out.println("\n------------------------------------------------");
          System.out.println("param=[[" + elem.getKey() + "]]");
          for (String value: elem.getValue()) {
            System.out.println("value=[[" + value + "]]");
          }
          System.out.println();
        }
        */
        
        response.setContentType("text/xml;charset=UTF-8");

        final ServletContext context = request.getServletContext();
        final boolean maintenanceMode =
                              (Boolean)context.getAttribute("MAINTENANCE_MODE");
        
        try (PrintWriter out = response.getWriter()) {
            final String maintenance = request.getParameter("maintenance");
            if (maintenance != null) {
                final Boolean maint = Boolean.valueOf(maintenance);

                context.setAttribute("MAINTENANCE_MODE", maint);
                if (maint) { // maintenance mode is on
                    updaterService.stop();
                } else { // maintenance mode is off
                    updaterService.start();
                }
                out.println("<result>MAINTENANCE_MODE=" + maint + "</result>");
                return;
            }
            if (maintenanceMode) {
                out.println("<WARNING>System in maintenance mode</WARNING>");
                return;
            }
                        
            // Ad hoc Similar Docs
            final String adhocSimilarDocs = request.getParameter("adhocSimilarDocs");
            if (adhocSimilarDocs != null) {
                if (adhocSimilarDocs.trim().isEmpty()) {
                    out.println("<ERROR>missing 'adhocSimDocs' parameter</ERROR>");
                } else {
                    final String outFields = request.getParameter("outFields");
                    final String[] oFields = (outFields == null)
                                    ? new String[0]: outFields.split(" *\\, *");
                    Set<String> fields = new HashSet<>();
                    for (String fld: oFields) {
                        fields.add(fld);
                    }
                    final String lastDaysPar = request.getParameter("lastDays");
                    final int lastDays = (lastDaysPar == null) ? 365 : 
                                           Integer.parseInt(lastDaysPar);
                    if (lastDays <= 0) {
                        out.println("<ERROR>'lastDays' parameter should be > 0</ERROR>");
                        return;
                    }
                    out.println(simSearch.search(adhocSimilarDocs, fields.toSet(), lastDays));
                }
                return;          
            }
            
            final String psId = request.getParameter("psId");
            if ((psId == null) || (psId.trim().isEmpty())) {
                out.println("<ERROR>missing 'psId' parameter</ERROR>");
                return;
            }

            // Add Profile
            final String addProfile = request.getParameter("addProfile");
            if (addProfile != null) {
                final String sentence = request.getParameter("sentence");
                if (addProfile.trim().isEmpty()) {
                    out.println("<ERROR>missing 'addProfile' parameter</ERROR>");
                } else if ((sentence == null) || 
                           (sentence.trim().isEmpty())) {
                    out.println("<ERROR>missing 'sentence' parameter</ERROR>");
                } else {
                    topIndex.addProfile(psId, addProfile, sentence);
                    out.println("<result>OK</result>");
                }
                return;
            }

            // Delete Profile
            final String deleteProfile = request.getParameter("deleteProfile");
            if (deleteProfile != null) {
                if (deleteProfile.trim().isEmpty()) {
                    out.println("<ERROR>missing 'deleteProfile' parameter</ERROR>");
                } else {
                    topIndex.deleteProfile(psId, deleteProfile);
                    out.println("<result>OK</result>");
                }
                return;
            }

            // Get Similar Docs
            final String getSimDocs = request.getParameter("getSimDocs");
            if (getSimDocs != null) {
                if (getSimDocs.trim().isEmpty()) {
                    out.println("<ERROR>missing 'getSimDocs' parameter</ERROR>");
                } else {
                    final String[] profs = getSimDocs.split(" *\\, *");
                    Set<String> profiles = new HashSet<>();
                    for (String prof: profs) {
                            profiles.add(prof);
                    }
                    final String outFields = request.getParameter("outFields");
                    final String[] oFields = (outFields == null)
                                    ? new String[0]: outFields.split(" *\\, *");
                    Set<String> fields = new HashSet<>();
                    for (String fld: oFields) {
                        fields.add(fld);
                    }
                    final String lastDaysPar = request.getParameter("lastDays");
                    final int lastDays = (lastDaysPar == null) ? 365 : 
                                           Integer.parseInt(lastDaysPar);
                    out.println(topIndex.getSimDocsXml(psId, profiles.toSet(), 
                                              fields.toSet(), 10, lastDays));
                }
                return;
            }

            // Show Profiles
            final String showProfiles = request.getParameter("showProfiles");
            if (showProfiles == null) {                    
                usage(out);
            } else {
                out.println(topIndex.getProfilesXml(psId)); // showProfiles
            }
        }
    }

    private void usage(final PrintWriter out) {
        out.println("<SYNTAX>");
        out.println("SDService/?");
        out.println("--- and one of the following options: ---");
        out.println("psId=&lt;id&gt;&amp;addProfile=&lt;id&gt;&amp;sentence=&lt;sentence&gt;");
        out.println("psId=&lt;id&gt;&amp;deleteProfile=&lt;id&gt;");
        out.println("psId=&lt;id&gt;&amp;getSimDocs=&lt;profile&gt;,..,&lt;profile&gt;&amp;[outFields=&lt;field&gt;,...,&lt;field&gt;][&amp;lastDays=&lt;num&gt;]");
        out.println("psId=&lt;id&gt;&amp;showProfiles=true");
        out.println("adhocSimDocs=&lt;sentence&gt;[outFields=&lt;field&gt;,...,&lt;field&gt;][&amp;lastDays=&lt;num&gt;]");
        out.println("</SYNTAX>");
    }

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
