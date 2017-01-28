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
package org.yamj.plugin.imdb;

import static org.yamj.api.common.tools.ResponseTools.isNotOK;
import static org.yamj.api.common.tools.ResponseTools.isOK;
import static org.yamj.api.common.tools.ResponseTools.isTemporaryError;
import static org.yamj.plugin.api.Constants.SOURCE_IMDB;
import static org.yamj.plugin.api.Constants.UTF8;
import static org.yamj.plugin.api.metadata.MetadataTools.parseToDate;

import com.omertron.imdbapi.ImdbApi;
import com.omertron.imdbapi.ImdbException;
import com.omertron.imdbapi.model.*;
import java.io.IOException;
import java.util.*;
import net.sf.ehcache.Cache;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.api.common.http.CommonHttpClient;
import org.yamj.api.common.http.DigestedResponse;
import org.yamj.plugin.api.PluginExtensionException;
import org.yamj.plugin.api.model.ICombined;
import org.yamj.plugin.api.service.PluginConfigService;
import org.yamj.plugin.api.service.PluginLocaleService;
import org.yamj.plugin.api.tools.EhCacheWrapper;
import org.yamj.plugin.api.web.HTMLTools;
import org.yamj.plugin.api.web.TemporaryUnavailableException;

public class ImdbApiWrapper {
    
    private static final Logger LOG = LoggerFactory.getLogger(ImdbApiWrapper.class);
    private static final String API_ERROR = "IMDb error";
    private static final String HTML_SITE_FULL = "http://www.imdb.com/";
    private static final String HTML_TITLE = "title/";
    private static final String HTML_A_END = "</a>";
    private static final String HTML_A_START = "<a ";
    private static final String HTML_H5_END = ":</h5>";
    private static final String HTML_H5_START = "<h5>";
    private static final String HTML_DIV_END = "</div>";

    private final ImdbApi imdbApi;
    private final PluginConfigService configService;
    private final PluginLocaleService localeService;
    private final CommonHttpClient httpClient;
    private final EhCacheWrapper cache;
    
    public ImdbApiWrapper(ImdbApi imdbApi, PluginConfigService configService, PluginLocaleService localeService, CommonHttpClient httpClient, Cache cache) {
        this.imdbApi = imdbApi;
        this.configService = configService;
        this.localeService = localeService;
        this.httpClient = httpClient;
        this.cache = new EhCacheWrapper(cache);
    }
    
    private static String getImdbUrl(String imdbId) {
        return getImdbUrl(imdbId, null);
    }

    private static String getImdbUrl(String imdbId, String site) {
        String url = HTML_SITE_FULL + HTML_TITLE + imdbId + "/";
        if (site != null) {
            url = url + site;
        }
        return url;
    }

    public ImdbMovieDetails getMovieDetails(String imdbId, Locale locale, boolean throwTempError) {
        ImdbMovieDetails movieDetails = null;
        try {
            movieDetails = imdbApi.getFullDetails(imdbId, locale);
        } catch (ImdbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed to get movie details using IMDb ID {}: {}", imdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return movieDetails;
    }
        
    public String getMovieDetailsXML(final String imdbId, boolean throwTempError) throws IOException {
        try {
            final String cacheKey = "moviexml###+imdbId";
            String result = cache.get(cacheKey, String.class);
            if (StringUtils.isBlank(result)) {
                DigestedResponse response = httpClient.requestContent(getImdbUrl(imdbId), UTF8);
                checkTempError(throwTempError, response);
                result = response.getContent();
                cache.store(cacheKey, result);
            }
            return result;
        } catch (RuntimeException | IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PluginExtensionException("IMDb request failed", ex);
        }
    }
    
    public List<ImdbCredit> getFullCast(String imdbId) {
        List<ImdbCredit> fullCast = null;
        try {
            // use US locale to check for uncredited cast
            fullCast = imdbApi.getFullCast(imdbId, Locale.US);
        } catch (ImdbException ex) {
            LOG.error("Failed to get full cast using IMDb ID {}: {}", imdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return fullCast;
    }

    public ImdbPerson getPerson(String imdbId, Locale locale, boolean throwTempError) {
        ImdbPerson imdbPerson = null;
        try {
            final String cacheKey = "person###"+imdbId+"###"+locale.getLanguage();
            imdbPerson = cache.get(cacheKey, ImdbPerson.class);
            if (imdbPerson == null) {
                imdbPerson = imdbApi.getActorDetails(imdbId, locale);
                cache.store(cacheKey, imdbPerson);
            }
        } catch (ImdbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed to get person details using IMDb ID {}: {}", imdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return imdbPerson;
    }

    public List<ImdbFilmography> getFilmopgraphy(String imdbId, Locale locale, boolean throwTempError) {
        List<ImdbFilmography> imdbFilmography = null;
        try {
            imdbFilmography = imdbApi.getActorFilmography(imdbId, locale);
        } catch (ImdbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed to get filmography using IMDb ID {}: {}", imdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return imdbFilmography;
    }

    @SuppressWarnings("unchecked")
	public Map<String,Integer> getTop250(Locale locale, boolean throwTempError) {
        try {
            final String cacheKey = "top250###"+locale.getLanguage();
            HashMap<String,Integer> result = cache.get(cacheKey, HashMap.class);
            if (result == null) {
                result = new HashMap<>();
                int rank = 0;
                for (ImdbList imdbList : imdbApi.getTop250(locale)) {
                    rank++;
                    if (StringUtils.isNotBlank(imdbList.getImdbId())) {
                        result.put(imdbList.getImdbId(), Integer.valueOf(rank));
                    }
                }
                cache.store(cacheKey, result);
            }
            return result;
        } catch (ImdbException ex) {
            checkTempError(throwTempError, ex);
            LOG.error("Failed to get Top250: {}", ex.getMessage());
            LOG.trace(API_ERROR, ex);
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
	public List<ImdbImage> getTitlePhotos(String imdbId, Locale locale) {
        List<ImdbImage> titlePhotos = null;
        try {
            final String cacheKey = "titlephotos###"+imdbId+"###"+locale.getLanguage();
            titlePhotos = cache.get(cacheKey, List.class);
            if (titlePhotos == null) {
                titlePhotos = imdbApi.getTitlePhotos(imdbId, locale);
                cache.store(cacheKey, titlePhotos);
            }
        } catch (ImdbException ex) {
            LOG.error("Failed to get title photos using IMDb ID {}: {}", imdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        return titlePhotos == null ? new ArrayList<ImdbImage>(0) : titlePhotos;
    }

    @SuppressWarnings("unchecked")
	public Map<Integer,List<ImdbEpisodeDTO>> getTitleEpisodes(String imdbId, Locale locale) {
        final String cacheKey = "episodes###"+imdbId+"###"+locale.getLanguage();
        Map<Integer,List<ImdbEpisodeDTO>> result = cache.get(cacheKey, Map.class);
        if (result != null) {
            return result;
        }
                        
        List<ImdbSeason> seasons = null;
        try {
            seasons = imdbApi.getTitleEpisodes(imdbId, locale);
        } catch (ImdbException ex) {
            LOG.error("Failed to get title episodes using IMDb ID {}: {}", imdbId, ex.getMessage());
            LOG.trace(API_ERROR, ex);
        }
        
        // if nothing found, then return nothing
        if (seasons == null) {
            return null;
        }

        result = new HashMap<>();
        for (ImdbSeason season : seasons) {
            if (StringUtils.isNumeric(season.getToken())) {
                Integer seasonId = Integer.valueOf(season.getToken());
                List<ImdbEpisodeDTO> episodes = new ArrayList<>();
                int episodeCounter = 0;
                for (ImdbMovie movie : season.getEpisodes()) {
                    ImdbEpisodeDTO episode = new ImdbEpisodeDTO();
                    episode.setEpisode(++episodeCounter);
                    episode.setImdbId(movie.getImdbId());
                    episode.setTitle(movie.getTitle());
                    episode.setYear(movie.getYear());
                    episode.setRelease(locale.getCountry(), parseToDate(movie.getReleaseDate()));
                    episodes.add(episode);
                }
                result.put(seasonId, episodes);
            }
        }
        cache.store(cacheKey, result);
        return result;
    }

    public String getReleasInfoXML(final String imdbId) {
        String webpage = null;
        try {
            final DigestedResponse response = httpClient.requestContent(getImdbUrl(imdbId, "releaseinfo"), UTF8);
            if (isOK(response)) {
                webpage = response.getContent();
            } else {
                LOG.warn("Requesting release infos failed with status {}: {}", response.getStatusCode(), imdbId);
            }
        } catch (Exception ex) {
            LOG.error("Requesting release infos failed: " + imdbId, ex);
        }
        return webpage;
    }

    public String getPersonBioXML(final String imdbId, boolean throwTempError) throws IOException {
        DigestedResponse response;
        try {
            response = httpClient.requestContent(HTML_SITE_FULL + "name/" + imdbId + "/bio", UTF8);
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PluginExtensionException("IMDb request failed", ex);
        }

        checkTempError(throwTempError, response);
        return response.getContent();
    }

    public Set<String> getProductionStudios(String imdbId) {
        Set<String> studios = new LinkedHashSet<>();
        try {
            DigestedResponse response = httpClient.requestContent(getImdbUrl(imdbId, "companycredits"), UTF8);
            if (isOK(response)) {
                List<String> tags = HTMLTools.extractTags(response.getContent(), "Production Companies</h4>", "</ul>", HTML_A_START, HTML_A_END);
                for (String tag : tags) {
                    studios.add(HTMLTools.removeHtmlTags(tag));
                }
            } else {
                LOG.warn("Requesting studios failed with status {}: {}", response.getStatusCode(), imdbId);
            }
        } catch (Exception ex) {
            LOG.error("Failed to retrieve studios: " + imdbId, ex);
        }
        return studios;
    }

    public void parseCertifications(ICombined combined, Locale locale, ImdbMovieDetails movieDetails) {
        final String imdbId = combined.getId(SOURCE_IMDB);
        
        // get certificate from IMDb API movie details
        String certificate = movieDetails.getCertificate().get("certificate");
        if (StringUtils.isNotBlank(certificate)) {
            String country = movieDetails.getCertificate().get("country");
            if (StringUtils.isBlank(country)) {
                combined.addCertification(locale.getCountry(), certificate);
            }
        }
        
        try {
            DigestedResponse response = httpClient.requestContent(getImdbUrl(imdbId, "parentalguide#certification"), UTF8);
            if (isNotOK(response)) {
                LOG.warn("Requesting certifications failed with status {}: {}", response.getStatusCode(), imdbId);
            } else {
                if (this.configService.getBooleanProperty("yamj3.certification.mpaa", false)) {
                    String mpaa = HTMLTools.extractTag(response.getContent(), "<h5><a href=\"/mpaa\">MPAA</a>:</h5>", 1);
                    if (StringUtils.isNotBlank(mpaa)) {
                        String key = "Rated ";
                        int pos = mpaa.indexOf(key);
                        if (pos != -1) {
                            int start = key.length();
                            pos = mpaa.indexOf(" on appeal for ", start);
                            if (pos == -1) {
                                pos = mpaa.indexOf(" for ", start);
                            }
                            if (pos != -1) {
                                combined.addCertification("MPAA", mpaa.substring(start, pos));
                            }
                        }
                    }
                }

                List<String> tags = HTMLTools.extractTags(response.getContent(), HTML_H5_START + "Certification" + HTML_H5_END, HTML_DIV_END,
                                "<a href=\"/search/title?certificates=", HTML_A_END);
                Collections.reverse(tags);
                for (String countryCode : localeService.getCertificationCountryCodes(locale)) {
                    loop: for (String country : localeService.getCountryNames(countryCode)) {
                        certificate = getPreferredValue(tags, country);
                        if (StringUtils.isNotBlank(certificate)) {
                            combined.addCertification(countryCode, certificate);
                            break loop;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("Failed to retrieve certifications: " + imdbId, ex);
        }
    }

    private static String getPreferredValue(List<String> tags, String preferredCountry) {
        String value = null;

        for (String text : tags) {
            String country = null;

            int pos = text.indexOf(':');
            if (pos != -1) {
                country = text.substring(0, pos);
                text = text.substring(pos + 1);
            }
            pos = text.indexOf('(');
            if (pos != -1) {
                text = text.substring(0, pos).trim();
            }

            if (country == null) {
                if (StringUtils.isEmpty(value)) {
                    value = text;
                }
            } else if (country.equals(preferredCountry)) {
                value = text;
                // No need to continue scanning
                break;
            }
        }
        return HTMLTools.stripTags(value);
    }

    public void parseAwards(ICombined combined) {
        final String imdbId = combined.getId(SOURCE_IMDB);
        
        try {
            DigestedResponse response = httpClient.requestContent(ImdbApiWrapper.getImdbUrl(imdbId, "awards"), UTF8);
            if (isNotOK(response)) {
                LOG.warn("Requesting certifications failed with status {}: {}", response.getStatusCode(), imdbId);
            } else if (response.getContent().contains("<h1 class=\"header\">Awards</h1>")) {
                List<String> awardBlocks = HTMLTools.extractTags(response.getContent(), "<h1 class=\"header\">Awards</h1>", "<div class=\"article\"", "<h3>", "</table>", false);

                for (String awardBlock : awardBlocks) {
                    //String realEvent = awardBlock.substring(0, awardBlock.indexOf('<')).trim();
                    String event = StringUtils.trimToEmpty(HTMLTools.extractTag(awardBlock, "<span class=\"award_category\">", "</span>"));
  
                    String tmpString = HTMLTools.extractTag(awardBlock, "<a href=", HTML_A_END).trim();
                    tmpString = tmpString.substring(tmpString.indexOf('>') + 1).trim();
                    int year = NumberUtils.isNumber(tmpString) ? Integer.parseInt(tmpString) : -1;
  
                    boolean awardWon = true;
                    for (String outcomeBlock : HTMLTools.extractHtmlTags(awardBlock, "<table class=", null, "<tr>", "</tr>")) {
                        String outcome = HTMLTools.extractTag(outcomeBlock, "<b>", "</b>");
                        
                        if (StringUtils.isNotBlank(outcome)) {
                            awardWon = "won".equalsIgnoreCase(outcome);
                        }
                        
                        String category = StringUtils.trimToEmpty(HTMLTools.extractTag(outcomeBlock, "<td class=\"award_description\">", "<br />"));
                        // Check to see if there was a missing title and just the name in the result
                        if (category.contains("href=\"/name/")) {
                            category = StringUtils.trimToEmpty(HTMLTools.extractTag(outcomeBlock, "<span class=\"award_category\">", "</span>"));
                        }

                        combined.addAward(event, category, year, awardWon, !awardWon);
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("Failed to retrieve awards: " + imdbId, ex);
        }
    }

    private static void checkTempError(boolean throwTempError, DigestedResponse response) {
        if (throwTempError && isTemporaryError(response)) {
            throw new TemporaryUnavailableException("IMDb service is temporary not available: " + response.getStatusCode());
        } else if (isNotOK(response)) {
            throw new PluginExtensionException("IMDb request failed: " + response.getStatusCode());
        }
    }

    private static void checkTempError(boolean throwTempError, ImdbException ex) {
        if (throwTempError && isTemporaryError(ex)) {
            throw new TemporaryUnavailableException("IMDb service temporary not available: " + ex.getResponseCode(), ex);
        }
    }
}   
