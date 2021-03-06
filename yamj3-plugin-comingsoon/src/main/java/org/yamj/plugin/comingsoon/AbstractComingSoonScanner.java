/*
 *      Copyright (c) 2004-2016 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ) plugins.
 *
 *      YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3-plugins
 *
 */
package org.yamj.plugin.comingsoon;

import static org.yamj.api.common.tools.ResponseTools.isNotOK;
import static org.yamj.api.common.tools.ResponseTools.isTemporaryError;
import static org.yamj.plugin.api.Constants.SOURCE_IMDB;
import static org.yamj.plugin.api.Constants.UTF8;
import static org.yamj.plugin.api.metadata.MetadataTools.isOriginalTitleScannable;

import java.io.IOException;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.api.common.http.DigestedResponse;
import org.yamj.plugin.api.NeedsConfigService;
import org.yamj.plugin.api.NeedsHttpClient;
import org.yamj.plugin.api.metadata.NfoScanner;
import org.yamj.plugin.api.model.IMovie;
import org.yamj.plugin.api.model.ISeries;
import org.yamj.plugin.api.model.IdMap;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.service.PluginMetadataService;
import org.yamj.plugin.api.web.HTMLTools;
import org.yamj.plugin.api.web.SearchEngineTools;
import org.yamj.plugin.api.web.TemporaryUnavailableException;
 
public abstract class AbstractComingSoonScanner implements NfoScanner, NeedsConfigService, NeedsHttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractComingSoonScanner.class);
    protected static final String SCANNER_NAME = "comingsoon";
    protected static final String COMINGSOON_BASE_URL = "http://www.comingsoon.it/";
    private static final String COMINGSOON_SEARCH_MOVIE = "film/?";
    private static final String COMINGSOON_SEARCH_SERIES = "serietv/ricerca/?";
    private static final String COMONGSOON_TITLE_PARAM = "titolo=";
    private static final String COMINGSOON_YEAR_PARAM = "anno=";
    protected static final String COMINGSOON_KEY_PARAM = "key=";
    private static final int COMINGSOON_MAX_DIFF = 1000;
    private static final int COMINGSOON_MAX_SEARCH_PAGES = 5;
    private static final String HTML_LI_END = "</li>";

    protected PluginConfigService configService;
    protected PluginMetadataService metadataService;
    protected CommonHttpClient httpClient;
    private SearchEngineTools searchEngineTools;
    
    @Override
    public final String getScannerName() {
        return SCANNER_NAME;
    }

    @Override
    public final void setConfigService(PluginConfigService configService) {
        this.configService = configService;
    }

    @Override
    public final void setHttpClient(CommonHttpClient httpClient) {
        this.httpClient = httpClient;
        this.searchEngineTools = new SearchEngineTools(httpClient, Locale.ITALY);
    }

    public String getMovieId(IMovie movie, boolean throwTempError) {
        String comingSoonId = movie.getId(SCANNER_NAME);
        if (isValidComingSoonId(comingSoonId)) {
            return comingSoonId;
        }
        
        // search coming soon site by title
        comingSoonId = getComingSoonId(movie.getTitle(), movie.getYear(), false, throwTempError);

        // search coming soon site by original title
        if (isNoValidComingSoonId(comingSoonId) && isOriginalTitleScannable(movie)) {
            comingSoonId = getComingSoonId(movie.getOriginalTitle(), movie.getYear(), false, throwTempError);
        }

        // search coming soon with search engine tools
        if (isNoValidComingSoonId(comingSoonId)) {
            comingSoonId = this.searchEngineTools.searchURL(movie.getTitle(), movie.getYear(), "www.comingsoon.it/film", throwTempError);
            int beginIndex = StringUtils.indexOf(comingSoonId, "film/");
            if (beginIndex < 0) {
                comingSoonId = null;
            } else {
                beginIndex = comingSoonId.indexOf("/", beginIndex+6);
                int endIndex = comingSoonId.indexOf("/", beginIndex+1);
                if (beginIndex < endIndex) {
                    comingSoonId = comingSoonId.substring(beginIndex+1, endIndex);
                } else {
                    comingSoonId = null;
                }
            }
        }
        
        if (isValidComingSoonId(comingSoonId)) {
            movie.addId(SCANNER_NAME, comingSoonId);
            return comingSoonId;
        }
        return null;
    }

    public String getSeriesId(ISeries series, boolean throwTempError) {
        String comingSoonId = series.getId(SCANNER_NAME);
        if (isValidComingSoonId(comingSoonId)) {
            return comingSoonId;
        }
        
        // search coming soon site by title
        comingSoonId = getComingSoonId(series.getTitle(), series.getStartYear(), true, throwTempError);

        // search coming soon site by original title
        if (isNoValidComingSoonId(comingSoonId) && isOriginalTitleScannable(series)) {
            comingSoonId = getComingSoonId(series.getOriginalTitle(), series.getStartYear(), true, throwTempError);
        }

        // search coming soon with search engine tools
        if (isNoValidComingSoonId(comingSoonId)) {
            comingSoonId = this.searchEngineTools.searchURL(series.getTitle(), series.getStartYear(), "www.comingsoon.it/serietv", throwTempError);
            int beginIndex = comingSoonId.indexOf("serietv/");
            if (beginIndex < 0) {
                comingSoonId = null;
            } else {
                beginIndex = comingSoonId.indexOf("/", beginIndex+9);
                int endIndex = comingSoonId.indexOf("/", beginIndex+1);
                if (beginIndex < endIndex) {
                    comingSoonId = comingSoonId.substring(beginIndex+1, endIndex);
                } else {
                    comingSoonId = null;
                }
            }
        }
        
        if (isValidComingSoonId(comingSoonId)) {
            series.addId(SCANNER_NAME, comingSoonId);
            return comingSoonId;
        }
        return null;
    }

    @Override
    public boolean scanNFO(String nfoContent, IdMap idMap) {
        if (configService.getBooleanProperty("comingsoon.search.imdb", false)) {
            metadataService.scanNFO(SOURCE_IMDB, nfoContent, idMap);
        }

        // if we already have the ID, skip the scanning of the NFO file
        final boolean ignorePresentId = configService.getBooleanProperty("comingsoon.nfo.ignore.present.id", false);
        if (!ignorePresentId && isValidComingSoonId(idMap.getId(SCANNER_NAME))) {
            return true;
        }

        LOG.trace("Scanning NFO for ComingSoon ID");

        int beginIndex = nfoContent.indexOf("?key=");
        if (beginIndex != -1) {
            String id = new StringTokenizer(nfoContent.substring(beginIndex + 5), "/ \n,:!&é\"'(--è_çà)=$").nextToken();
            LOG.debug("ComingSoon ID found in NFO: {}", id);
            idMap.addId(SCANNER_NAME, id);
            return true;
        }
        
        LOG.debug("No ComingSoon ID found in NFO");
        return false;
    }

    protected static boolean isValidComingSoonId(String comingSoonId) {
        if (StringUtils.isBlank(comingSoonId)) {
            return false;
        }
        return !StringUtils.equalsIgnoreCase(comingSoonId, "na");
    }

    protected static boolean isNoValidComingSoonId(String comingSoonId) {
        if (StringUtils.isBlank(comingSoonId)) {
            return true;
        }
        return StringUtils.equalsIgnoreCase(comingSoonId, "na");
    }

    protected String getComingSoonId(String title, int year, boolean tvShow, boolean throwTempError) {
        return getComingSoonId(title, year, COMINGSOON_MAX_DIFF, tvShow, throwTempError);
    }

    private String getComingSoonId(String title, int year, int scoreToBeat, boolean tvShow, boolean throwTempError) {
        if (scoreToBeat == 0) {
            return null;
        }
        int currentScore = scoreToBeat;

        try {
            StringBuilder urlBase = new StringBuilder(COMINGSOON_BASE_URL);
            if (tvShow) {
                urlBase.append(COMINGSOON_SEARCH_SERIES);
            } else {
                urlBase.append(COMINGSOON_SEARCH_MOVIE);
            }
            urlBase.append(COMONGSOON_TITLE_PARAM);
            urlBase.append(HTMLTools.encodeUrl(title.toLowerCase()));

            if (year > 0 ) {
                urlBase.append("&").append(COMINGSOON_YEAR_PARAM);
                urlBase.append(year);
            }
            
            int searchPage = 0;
            String comingSoonId = null;
            
            while (searchPage++ < COMINGSOON_MAX_SEARCH_PAGES) {

                StringBuilder urlPage = new StringBuilder(urlBase);
                if (searchPage > 1) {
                    urlPage.append("&p=").append(searchPage);
                }

                LOG.debug("Fetching ComingSoon search page {}/{} - URL: {}", searchPage, COMINGSOON_MAX_SEARCH_PAGES, urlPage.toString());
                DigestedResponse response = httpClient.requestContent(urlPage.toString(), UTF8);
                if (throwTempError && isTemporaryError(response)) {
                    throw new TemporaryUnavailableException("ComingSoon service is temporary not available: " + response.getStatusCode());
                } else if (isNotOK(response)) {
                    LOG.error("Can't find ComingSoon ID due response status {}", response.getStatusCode());
                    return null;
                }

                List<String[]> resultList = parseComingSoonSearchResults(response.getContent(), tvShow);
                if (resultList.isEmpty()) {
                    break;
                }
                
                for (int i = 0; i < resultList.size() && currentScore > 0; i++) {
                    String lId = resultList.get(i)[0];
                    String lTitle = resultList.get(i)[1];
                    String lOrig = resultList.get(i)[2];
                    int difference = compareTitles(title, lTitle);
                    int differenceOrig = compareTitles(title, lOrig);
                    difference = differenceOrig < difference ? differenceOrig : difference;
                    if (difference < currentScore) {
                        if (difference == 0) {
                            LOG.debug("Found perfect match for: {}, {}", lTitle, lOrig);
                            searchPage = COMINGSOON_MAX_SEARCH_PAGES; //ends loop
                        } else {
                            LOG.debug("Found a match for: {}, {}, difference {}", lTitle, lOrig, difference);
                        }
                        comingSoonId = lId;
                        currentScore = difference;
                    }
                }
            }

            if (year>0 && currentScore>0) {
                LOG.debug("Perfect match not found, trying removing by year ...");
                String newComingSoonId = getComingSoonId(title, -1, currentScore, tvShow, throwTempError);
                comingSoonId = isNoValidComingSoonId(newComingSoonId) ? comingSoonId : newComingSoonId;
            }

            if (StringUtils.isNotBlank(comingSoonId)) {
                LOG.debug("Found valid ComingSoon ID: {}", comingSoonId);
            }

            return comingSoonId;

        } catch (IOException ex) {
            LOG.error("Failed retrieving ComingSoon id for title '{}': {}", title, ex.getMessage());
            LOG.trace("ComingSoon service error", ex);
            return null;
        }
    }

    /**
     * Parse the search results
     *
     * Search results end with "Trovati NNN Film" (found NNN movies).
     *
     * After this string, more movie URL are found, so we have to set a boundary
     *
     * @param xml
     * @return
     */
    private static List<String[]> parseComingSoonSearchResults(String xml, boolean tvShow) {
        final List<String[]> result = new ArrayList<>();
        
        int beginIndex = StringUtils.indexOfIgnoreCase(xml, "Trovate");
        int resultsFound = -1;
        if (beginIndex > 0) {
            int end = StringUtils.indexOfIgnoreCase(xml, tvShow?" serie tv":" film", beginIndex + 7);
            if (end > 0) {
                String tmp = HTMLTools.stripTags(xml.substring(beginIndex + 8, end));
                resultsFound = NumberUtils.toInt(tmp, -1);
            }
        }

        if (resultsFound < 0) {
            LOG.error("Couldn't find 'TROVATE NNN "+(tvShow?"SERIE TV":"FILM")+" IN ARCHIVIO' string. Search page layout probably changed");
            return result;
        }
 
        List<String> searchResults = HTMLTools.extractTags(xml, "box-lista-cinema", "BOX FILM RICERCA", "<a h", "</a>", false);
        if (searchResults == null || searchResults.isEmpty()) {
            return result;
        }
        
        LOG.debug("Search found {} results", searchResults.size());

        for (String searchResult : searchResults) {
            String comingSoonId = null;
            if (tvShow) {
                beginIndex = searchResult.indexOf("ref=\"/serietv/");
            } else {
                beginIndex = searchResult.indexOf("ref=\"/film/");
            }
            
            if (beginIndex >= 0) {
                comingSoonId = getComingSoonIdFromURL(searchResult);
            }
            
            if (StringUtils.isBlank(comingSoonId)) {
                continue;
            }

            String title = HTMLTools.extractTag(searchResult, "<div class=\"h5 titolo cat-hover-color anim25\">", "</div>");
            if (StringUtils.isBlank(title)) {
                continue;
            }
            
            String originalTitle = HTMLTools.extractTag(searchResult, "<div class=\"h6 sottotitolo\">", "</div>");
            originalTitle = StringUtils.trimToEmpty(originalTitle);
            if (originalTitle.startsWith("(")) {
                originalTitle = originalTitle.substring(1, originalTitle.length() - 1).trim();
            }
            
            String year = null;
            beginIndex = searchResult.indexOf("ANNO</span>:");
            if (beginIndex > 0) {
                int endIndex = searchResult.indexOf(HTML_LI_END, beginIndex);
                if (endIndex > 0) {
                    year = searchResult.substring(beginIndex + 12, endIndex).trim();
                }
            }
            
            result.add(new String[]{comingSoonId, title, originalTitle, year});
        }

        return result;
    }

    private static String getComingSoonIdFromURL(String url) {
        int index = url.indexOf("/scheda");
        if (index > -1) {
            String stripped = url.substring(0, index);
            index = StringUtils.lastIndexOf(stripped, '/');
            if (index > -1) {
                return stripped.substring(index + 1);
            }
        }
        return null;
    }

    /**
     * Returns difference between two titles.
     *
     * Since ComingSoon returns strange results on some researches, difference
     * is defined as follows: abs(word count difference) - (searchedTitle wordcount - matched words)
     *
     * @param searchedTitle
     * @param returnedTitle
     * @return
     */
    private static int compareTitles(String searchedTitle, String returnedTitle) {
        if (StringUtils.isBlank(returnedTitle)) {
            return COMINGSOON_MAX_DIFF;
        }
        LOG.trace("Comparing {} and {}", searchedTitle, returnedTitle);

        String title1 = searchedTitle.toLowerCase().replaceAll("[,.\\!\\?\"']", "");
        String title2 = returnedTitle.toLowerCase().replaceAll("[,.\\!\\?\"']", "");
        return StringUtils.getLevenshteinDistance(title1, title2);
    }

    protected static String parseTitleOriginal(String xml) {
        String titleOriginal = HTMLTools.extractTag(xml, "Titolo originale:", "</p>").trim();
        if (titleOriginal.startsWith("(")) {
            titleOriginal = titleOriginal.substring(1, titleOriginal.length() - 1).trim();
        }
        return titleOriginal;
    }
    
    protected static String parsePlot(String xml) {
        int beginIndex = xml.indexOf("<div class=\"contenuto-scheda-destra");
        if (beginIndex < 0) {
            return null;
        }
        
        int endIndex = xml.indexOf("<div class=\"box-descrizione\"", beginIndex);
        if (endIndex < 0) {
            return null;
        }

        return  HTMLTools.stripTags(HTMLTools.extractTag(xml.substring(beginIndex, endIndex), "<p>", "</p>"));
    }
    
    protected static int parseRating(String xml) {
        String rating = HTMLTools.extractTag(xml, "<span itemprop=\"ratingValue\">", "</span>");
        if (StringUtils.isNotBlank(rating)) {
            // Rating is 0 to 5, we normalize to 100
            return (int) (NumberUtils.toFloat(rating.replace(',', '.'), -1.0f) * 20);
        }
        return -1;
    }

    protected Collection<String> parseCountries(String xml) {
        final String country = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">PAESE</span>:", HTML_LI_END)).trim();
        return Collections.singleton(country);
    }
    
    protected static Collection<String> parseStudios(String xml) {
        final String studioList = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">PRODUZIONE</span>: ", HTML_LI_END));
        if (StringUtils.isBlank(studioList)) {
            return null; //NOSONAR
        }
        
        Collection<String> studioNames = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(studioList, ",");
        while (st.hasMoreTokens()) {
            studioNames.add(st.nextToken().trim());
        }
        return studioNames;
    }
    
    protected static Collection<String> parseGenres(String xml) {
        final String genreList = HTMLTools.stripTags(HTMLTools.extractTag(xml, ">GENERE</span>: ", HTML_LI_END));
        if (StringUtils.isBlank(genreList)) {
            return null; //NOSONAR
        }
        
        Collection<String> genreNames = new ArrayList<>();
        StringTokenizer st = new StringTokenizer(genreList, ",");
        while (st.hasMoreTokens()) {
            genreNames.add(st.nextToken().trim());
        }
        return genreNames;
    }
}

