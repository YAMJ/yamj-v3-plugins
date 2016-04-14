package org.yamj.plugin.ofdb;

import java.io.IOException;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.api.common.http.DigestedResponse;
import org.yamj.api.common.tools.ResponseTools;
import org.yamj.plugin.api.PluginConfigService;
import org.yamj.plugin.api.metadata.Credit;
import org.yamj.plugin.api.metadata.Movie;
import org.yamj.plugin.api.metadata.MovieScanner;
import org.yamj.plugin.api.tools.Constants;
import org.yamj.plugin.api.tools.MetadataTools;
import org.yamj.plugin.api.type.JobType;
import org.yamj.plugin.api.web.HTMLTools;
import org.yamj.plugin.api.web.SearchEngineTools;
import org.yamj.plugin.api.web.TemporaryUnavailableException;
import ro.fortsoft.pf4j.Extension;

@Extension
public class OfdbMovieScanner implements MovieScanner {

    private static final Logger LOG = LoggerFactory.getLogger(OfdbMovieScanner.class);
    private static final String SCANNER_NAME = "ofdb";
    private static final String HTML_FONT = "</font>";
    private static final String HTML_TABLE_END = "</table>";
    private static final String HTML_TR_START = "<tr";
    private static final String HTML_TR_END = "</tr>";
    
    private PluginConfigService configService;
    private CommonHttpClient httpClient;
    private SearchEngineTools searchEngineTools;
    
    @Override
    public String getScannerName() {
        return SCANNER_NAME;
    }

    @Override
    public void init(PluginConfigService configService, CommonHttpClient httpClient, Locale locale) {
        this.configService = configService;
        this.httpClient = httpClient;

        this.searchEngineTools = new SearchEngineTools(httpClient, Locale.GERMANY);
        this.searchEngineTools.setSearchSites("google");
    }

    @Override
    public String getMovieId(String title, String originalTitle, int year, Map<String, String> ids, boolean throwTempError) {
        String ofdbUrl = ids.get(SCANNER_NAME);
        if (StringUtils.isNotBlank(ofdbUrl)) {
            return ofdbUrl;
        }
        
        // get and check IMDb id
        String imdbId = ids.get("imdb");
        if (StringUtils.isNotBlank(imdbId)) {
            // if IMDb id is present then use this
            ofdbUrl = getOfdbIdByImdbId(imdbId, throwTempError);
        }
        
        if (StringUtils.isBlank(ofdbUrl)) {
            // try by title and year
            ofdbUrl = getOfdbIdByTitleAndYear(title, year, throwTempError);
        }

        if (StringUtils.isBlank(ofdbUrl) && StringUtils.isNotBlank(originalTitle) && !StringUtils.equalsIgnoreCase(title, originalTitle)) {
            // try by original title and year
            ofdbUrl = getOfdbIdByTitleAndYear(originalTitle, year, throwTempError);
        }

        if (StringUtils.isBlank(ofdbUrl)) {
            // try with search engines (don't throw error if temporary not available)
            ofdbUrl = searchEngineTools.searchURL(title, year, "www.ofdb.de/film", false);
        }

        // add id in id map
        ids.put(SCANNER_NAME, ofdbUrl);
        
        return ofdbUrl;
    }

    private String getOfdbIdByImdbId(String imdbId, boolean throwTempError) {
        try {
            DigestedResponse response = httpClient.requestContent("http://www.ofdb.de/view.php?page=suchergebnis&SText=" + imdbId + "&Kat=IMDb", Constants.UTF8);
            if (throwTempError && ResponseTools.isTemporaryError(response)) {
                throw new TemporaryUnavailableException("OFDb service is temporary not available: " + response.getStatusCode());
            } else if (ResponseTools.isNotOK(response)) {
                LOG.error("Can't find movie id for imdb id due response status {}: {}", response.getStatusCode(), imdbId);
                return null;
            }

            final String xml = response.getContent();
            
            int beginIndex = xml.indexOf("Ergebnis der Suchanfrage");
            if (beginIndex < 0) {
                return null;
            }

            beginIndex = xml.indexOf("film/", beginIndex);
            if (beginIndex != -1) {
                StringBuilder sb = new StringBuilder();
                sb.append("http://www.ofdb.de/");
                sb.append(xml.substring(beginIndex, xml.indexOf('\"', beginIndex)));
                return sb.toString();
            }

        } catch (IOException ex) {
            LOG.error("Failed retrieving OFDb url for IMDb id {}: {}", imdbId, ex.getMessage());
            LOG.trace("OFDb service error", ex);
        }
        return null;
    }

    private String getOfdbIdByTitleAndYear(String title, int year, boolean throwTempError) {
        if (year <= 0) {
            // title and year must be present for successful OFDb advanced search
            // expected are 2 search parameters minimum; so skip here if year is not valid
            return null;
        }

        try {
            StringBuilder sb = new StringBuilder("http://www.ofdb.de/view.php?page=fsuche&Typ=N&AB=-&Titel=");
            sb.append(HTMLTools.encodePlain(title));
            sb.append("&Genre=-&HLand=-&Jahr=");
            sb.append(year);
            sb.append("&Wo=-&Land=-&Freigabe=-&Cut=A&Indiziert=A&Submit2=Suche+ausf%C3%BChren");

            
            DigestedResponse response = httpClient.requestContent(sb.toString(), Constants.UTF8);
            if (throwTempError && ResponseTools.isTemporaryError(response)) {
                throw new TemporaryUnavailableException("OFDb service is temporary not available: " + response.getStatusCode());
            } else if (ResponseTools.isNotOK(response)) {
                LOG.error("Can't find movie id by title and year due response status {}: '{}'-{}", response.getStatusCode(), title, year);
                return null;
            }

            final String xml = response.getContent();
            
            int beginIndex = xml.indexOf("Liste der gefundenen Fassungen");
            if (beginIndex < 0) {
                return null;
            }

            beginIndex = xml.indexOf("href=\"film/", beginIndex);
            if (beginIndex < 0) {
                return null;
            }

            sb.setLength(0);
            sb.append("http://www.ofdb.de/");
            sb.append(xml.substring(beginIndex + 6, xml.indexOf("\"", beginIndex + 10)));
            return sb.toString();

        } catch (IOException ex) {
            LOG.error("Failed retrieving OFDb url for title '{}': {}", title, ex.getMessage());
            LOG.trace("OFDb service error", ex);
        }
        return null;
    }

    @Override
    public boolean scanMovie(Movie movie, boolean throwTempError) {
        final String ofdbUrl = movie.getIds().get(getScannerName());
        if (StringUtils.isBlank(ofdbUrl)) {
            return false;
        }
        
        try {
            DigestedResponse response = httpClient.requestContent(ofdbUrl, Constants.UTF8);
            if (throwTempError && ResponseTools.isTemporaryError(response)) {
                throw new TemporaryUnavailableException("OFDb service is temporary not available: " + response.getStatusCode());
            } else if (ResponseTools.isNotOK(response)) {
                throw new RuntimeException("OFDb request failed: " + response.getStatusCode());
            }
            
            String xml = response.getContent();
            String title = HTMLTools.extractTag(xml, "<title>OFDb -", "</title>");
            // check for movie type change
            if (title.contains("[TV-Serie]")) {
                LOG.warn("{} is a TV Show, skipping", title);
                return false;
            }
            
            // set IMDb id
            String imdbId = HTMLTools.extractTag(xml, "href=\"http://www.imdb.com/Title?", "\"");
            movie.addId("imdb", "tt" + imdbId);
    
            String titleShort = HTMLTools.extractTag(xml, "<title>OFDb -", "</title>");
            if (titleShort.indexOf('(') > 0) {
                // strip year from title
                titleShort = titleShort.substring(0, titleShort.lastIndexOf('(')).trim();
            }
            movie.setTitle(titleShort);
    
            // scrape plot and outline
            String plotMarker = HTMLTools.extractTag(xml, "<a href=\"plot/", 0, "\"");
            if (StringUtils.isNotBlank(plotMarker) ) {
                response = httpClient.requestContent("http://www.ofdb.de/plot/" + plotMarker, Constants.UTF8);
                if (throwTempError && ResponseTools.isTemporaryError(response)) {
                    throw new TemporaryUnavailableException("OFDb service failed to get plot: " + response.getStatusCode());
                } else if (ResponseTools.isNotOK(response)) {
                    throw new RuntimeException("OFDb plot request failed: " + response.getStatusCode());
                }
                
                int firstindex = response.getContent().indexOf("gelesen</b></b><br><br>") + 23;
                int lastindex = response.getContent().indexOf(HTML_FONT, firstindex);
                String plot = response.getContent()
                                      .substring(firstindex, lastindex)
                                      .replaceAll("<br />", " ")
                                      .trim();
    
                movie.setPlot(plot).setOutline(plot);
            }
    
            // scrape additional informations
            int beginIndex = xml.indexOf("view.php?page=film_detail");
            if (beginIndex < 0) {
                // nothing to do anymore
                return true;
            }
            
            String detailUrl = "http://www.ofdb.de/" + xml.substring(beginIndex, xml.indexOf('\"', beginIndex));
            response = httpClient.requestContent(detailUrl, Constants.UTF8);
            if (throwTempError && ResponseTools.isTemporaryError(response)) {
                throw new TemporaryUnavailableException("OFDb service failed to get details: " + response.getStatusCode());
            } else if (ResponseTools.isNotOK(response)) {
                throw new RuntimeException("OFDb details request failed: " + response.getStatusCode());
            }
            
            // get detail XML
            xml = response.getContent();
            
            // resolve for additional informations
            List<String> tags = HTMLTools.extractHtmlTags(xml, "<!-- Rechte Spalte -->", HTML_TABLE_END, HTML_TR_START, HTML_TR_END);
    
            for (String tag : tags) {
                if (tag.contains("Originaltitel")) {
                    String scraped = HTMLTools.removeHtmlTags(HTMLTools.extractTag(tag, "class=\"Daten\">", HTML_FONT)).trim();
                    movie.setOriginalTitle(scraped);
                }
    
                if (tag.contains("Erscheinungsjahr")) {
                    String scraped = HTMLTools.removeHtmlTags(HTMLTools.extractTag(tag, "class=\"Daten\">", HTML_FONT)).trim();
                    movie.setYear(MetadataTools.toYear(scraped));
                }
    
                if (tag.contains("Genre(s)")) {
                    for (String genre : HTMLTools.extractHtmlTags(tag, "class=\"Daten\"", "</td>", "<a", "</a>")) {
                        movie.addGenre(HTMLTools.removeHtmlTags(genre).trim());
                    }
                }
    
                if (tag.contains("Herstellungsland")) {
                    for (String country : HTMLTools.extractHtmlTags(tag, "class=\"Daten\"", "</td>", "<a", "</a>")) {
                        movie.addCountry(HTMLTools.removeHtmlTags(country).trim());
                    }
                }
            }
    
            // DIRECTORS
            if (configService.isCastScanEnabled(JobType.DIRECTOR)) {
                tags = HTMLTools.extractHtmlTags(xml, "<i>Regie</i>", HTML_TABLE_END, HTML_TR_START, HTML_TR_END);
                for (String tag : tags) {
                    final String name = extractName(tag);
                    if (StringUtils.isNotBlank(name)) {
                        movie.addCredit(new Credit(JobType.DIRECTOR, name));
                    }
                }
            }
    
            // WRITERS
            if (configService.isCastScanEnabled(JobType.WRITER)) {
                tags = HTMLTools.extractHtmlTags(xml, "<i>Drehbuchautor(in)</i>", HTML_TABLE_END, HTML_TR_START, HTML_TR_END);
                for (String tag : tags) {
                    final String name = extractName(tag);
                    if (StringUtils.isNotBlank(name)) {
                        movie.addCredit(new Credit(JobType.WRITER, name));
                    }
                }
            }
    
            // ACTORS
            if (configService.isCastScanEnabled(JobType.ACTOR)) {
                tags = HTMLTools.extractHtmlTags(xml, "<i>Darsteller</i>", HTML_TABLE_END, HTML_TR_START, HTML_TR_END);
                for (String tag : tags) {
                    final String name = extractName(tag);
                    if (StringUtils.isNotBlank(name)) {
                        movie.addCredit(new Credit(JobType.ACTOR, name, extractRole(tag)));
                    }
                }
            }
            
            // everything is fine
            return true;
        } catch (IOException ioe) {
            throw new RuntimeException("OFDb scanning error", ioe);
        }
    }

    private static String extractName(String tag) {
        String name = HTMLTools.extractTag(tag, "class=\"Daten\">", HTML_FONT);
        int akaIndex = name.indexOf("als <i>");
        if (akaIndex > 0) {
            name = name.substring(0, akaIndex);
        }
        return HTMLTools.removeHtmlTags(name);
    }

    private static String extractRole(String tag) {
        String role = HTMLTools.extractTag(tag, "class=\"Normal\">", HTML_FONT);
        role = HTMLTools.removeHtmlTags(role);
        if (role.startsWith("... ")) {
            role = role.substring(4);
        }
        return role;
    }
    
    @Override
    public String scanNFO(String nfoContent) {
        int beginIndex = nfoContent.indexOf("http://www.ofdb.de/film/");
        if (beginIndex != -1) {
            StringTokenizer st = new StringTokenizer(nfoContent.substring(beginIndex), " \n\t\r\f!&é\"'(èçà)=$<>");
            return st.nextToken();
        }
        return null;
    }
}