/*=========================================================================

    SimilarDocs © Pan American Health Organization, 2018.
    See License at: https://github.com/bireme/SimilarDocs/blob/master/LICENSE.txt

  ==========================================================================*/

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
import scala.Option;

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
    
    private String sdIndexPath;
    private String topIndexPath;
    private String decsIndexPath;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        final ServletContext context = config.getServletContext();

        sdIndexPath = context.getInitParameter("SD_INDEX_PATH");
        if (sdIndexPath == null) throw new ServletException(
                                              "empty 'SD_INDEX_PATH' config");
        topIndexPath = context.getInitParameter("TOP_INDEX_PATH");
        if (topIndexPath == null) throw new ServletException(
                                              "empty 'TOP_INDEX_PATH' config");
        decsIndexPath = context.getInitParameter("DECS_INDEX_PATH");
        if (decsIndexPath == null) throw new ServletException(
                                              "empty 'DECS_INDEX_PATH' config");

        Tools.deleteLockFile(sdIndexPath);
        Tools.deleteLockFile(topIndexPath);
        Tools.deleteLockFile(decsIndexPath);

        simSearch = new SimDocsSearch(sdIndexPath, decsIndexPath);
        topIndex = new TopIndex(simSearch, topIndexPath);
                
        topIndex.resetAllTimes();
        /*topIndex.asyncUpdSimilarDocs(Conf.maxDocs(), Conf.sources(),
                                     Conf.instances());*/
      
        context.setAttribute("MAINTENANCE_MODE", Boolean.FALSE);
    }

    @Override
    public void destroy() {
        topIndex.close();
        simSearch.close();
        try {
            Thread.sleep(20000); 
        } catch (InterruptedException ex) {
            System.out.println(ex.toString());
        }
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
        response.setContentType("text/xml;charset=UTF-8");
        response.addHeader("Access-Control-Allow-Origin", "*");

        final ServletContext context = request.getServletContext();
        final boolean maintenanceMode =
                              (Boolean)context.getAttribute("MAINTENANCE_MODE");
        final String maintenance = request.getParameter("maintenance");
        final Boolean maint = (maintenance != null) ? Boolean.valueOf(maintenance)
                                                    : null;        

        try (PrintWriter out = response.getWriter()) {
            if (maint != null) {
                if (maint) { // requiring that maintenance mode be on
                    context.setAttribute("MAINTENANCE_MODE", true);
                    topIndex.close();
                    simSearch.close();
                    out.println("<result><maintenance_mode>true</maintenance_mode></result>");
                } else {
                    try {
                        topIndex.close();
                        simSearch.close();
                        simSearch = new SimDocsSearch(sdIndexPath, decsIndexPath);
                        topIndex = new TopIndex(simSearch, topIndexPath);
        
                        topIndex.resetAllTimes();
                        context.setAttribute("MAINTENANCE_MODE", false);
                        topIndex.asyncUpdSimilarDocs(Conf.maxDocs(), 
                                Conf.sources(), Conf.instances());                        
                        out.println("<result><maintenance_mode>false</maintenance_mode></result>");
                    } catch(Exception ex) {
                        out.println("<result><ERROR>" +
                                ex.toString() + "</ERROR></result>");
                    }
                }
                return;
            }
            if (maintenanceMode) {
                out.println("<WARNING>System is in maintenance mode</WARNING>");
                return;
            }

            // Ad hoc Similar Docs
            final String adhocSimilarDocs = request.getParameter("adhocSimilarDocs");
            if (adhocSimilarDocs != null) {
                if (adhocSimilarDocs.trim().isEmpty()) {
                    out.println("<ERROR>missing 'adhocSimDocs' parameter</ERROR>");
                } else {
                    final String explainPar = request.getParameter("explain");
                    final Boolean explain = (explainPar == null) ? false :
                                            (explainPar.trim().isEmpty()) ? true :
                                             Boolean.parseBoolean(explainPar);
                    
                    final String outFields = request.getParameter("outFields");
                    final String[] oFields = (outFields == null)
                                    ? new String[0]: outFields.split(" *\\, *");
                    Set<String> fields = new HashSet<>();
                    for (String fld: oFields) {
                        fields.addOne(fld);
                    }
                    if (explain) {                        
                        for (String indField: org.bireme.sd.Tools.setToArray(Conf.idxFldNames())) {
                            fields.addOne(indField);
                        }                 
                        fields.addOne("db");
                        fields.addOne("update_date");
                    }
                    final String maxDocsPar = request.getParameter("maxDocs");
                    final int maxDocs = (maxDocsPar == null) ? Conf.maxDocs() :
                                           Integer.parseInt(maxDocsPar);                   
                    
                    final String srcs = request.getParameter("sources");
                    final String[] sources = (srcs == null)
                                    ? new String[0]: srcs.split(" *\\, *");
                    Set<String> srcSet = new HashSet<>();
                    for (String src: sources) {
                        srcSet.add(src);
                    }
                    final String insts = request.getParameter("instances");
                    final String[] instances = (insts == null)
                                    ? new String[0]: insts.split(" *\\, *");
                    Set<String> instSet = new HashSet<>();
                    for (String inst: instances) {
                        instSet.add(inst);
                    }
                    final String lastDaysPar = request.getParameter("lastDays");
                    final int lastDays = (lastDaysPar == null) ? 0 :
                                           Integer.parseInt(lastDaysPar);
                    if (lastDays < 0) {
                        out.println("<ERROR>'lastDays' parameter should be >= 0</ERROR>");
                        return;
                    }
                    final String oneTimePer = request.getParameter("ignoreUpdateDate");
                    final boolean oneTimePeriod = (oneTimePer == null) ? false :
                            Boolean.parseBoolean(oneTimePer);
                    out.println(simSearch.search(adhocSimilarDocs, fields.toSet(),
                        maxDocs, srcSet.toSet(), instSet.toSet(), lastDays, 
                        explain, !oneTimePeriod));
                }
                return;
            }
            
            // Show Users
            final String showUsers = request.getParameter("showUsers");
            if (showUsers != null) {
                out.println(topIndex.getUsersXml()); // showUsers
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
                    Option<Object> beginDate;
                    if (request.getParameter("considerDate") == null) {
                        beginDate = scala.Option.apply(null);
                    } else {
                        final long beginTime = org.bireme.sd.Tools.getIahxModificationTime() - 
                               org.bireme.sd.Tools.daysToTime(Conf.excludeDays() + Conf.numDays());
                        beginDate = scala.Some.apply(beginTime);
                    }                            
                    Set<String> fields = new HashSet<>();
                    for (String fld: oFields) {
                        fields.add(fld);
                    }
                    
                    out.println(topIndex.getSimDocsXml(psId, profiles.toSet(),
                        fields.toSet(), Conf.maxDocs(), beginDate, 
                        Conf.sources(), Conf.instances()));
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
        out.println("psId=&lt;id&gt;&amp;getSimDocs=&lt;profile&gt;,..,&lt;profile&gt;[&amp;outFields=&lt;field&gt;,...,&lt;field&gt;]");
        out.println("psId=&lt;id&gt;&amp;showUsers=true");
        out.println("psId=&lt;id&gt;&amp;showProfiles=true");
        out.println("adhocSimilarDocs=&lt;sentence&gt;[outFields=&lt;field&gt;,...,&lt;field&gt;][maxDocs=&lt;num&gt;][sources=&lt;src&gt;,...,&lt;src&gt;][instances=&lt;inst&gt;,...,&lt;inst&gt;][&amp;lastDays=&lt;num&gt;][&amp;explain=&lt;bool&gt;][&amp;ignoreUpdateDate=&lt;bool&gt;]");
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
