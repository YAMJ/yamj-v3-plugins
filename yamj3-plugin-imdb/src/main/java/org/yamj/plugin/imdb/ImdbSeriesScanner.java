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

import static org.yamj.plugin.api.Constants.SOURCE_IMDB;

import com.omertron.imdbapi.model.ImdbMovieDetails;
import java.io.IOException;
import java.util.*;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.metadata.MetadataTools;
import org.yamj.plugin.api.metadata.SeriesScanner;
import org.yamj.plugin.api.model.IEpisode;
import org.yamj.plugin.api.model.ISeason;
import org.yamj.plugin.api.model.ISeries;
import org.yamj.plugin.api.web.HTMLTools;
import ro.fortsoft.pf4j.Extension;

@Extension
public final class ImdbSeriesScanner extends AbstractImdbScanner implements SeriesScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ImdbSeriesScanner.class);

    @Override
    public boolean isValidSeriesId(String seriesId) {
        return isValidImdbId(seriesId);
    }

    @Override
    public boolean scanSeries(ISeries series, boolean throwTempError) {
        // get movie id
        final String imdbId = series.getId(SOURCE_IMDB);
        if (isNoValidImdbId(imdbId)) {
            LOG.debug("IMDb id not available : {}", series.getTitle());
            return false;
        }

        try {
            LOG.debug("IMDb id available ({}), updating series", imdbId);
            return updateSeries(series, imdbId, throwTempError);
        } catch (IOException ioe) {
            LOG.error("IMDb service error: '" + series.getTitle() + "'", ioe);
            return false;
        }
    }

    private boolean updateSeries(ISeries series, String imdbId, boolean throwTempError) throws IOException {
        final Locale locale = localeService.getLocale();
        ImdbMovieDetails movieDetails = imdbApiWrapper.getMovieDetails(imdbId, locale, throwTempError);
        if (movieDetails == null || StringUtils.isBlank(movieDetails.getImdbId())) {
            return false;
        }
        
        // check type change
        if (!"tv_series".equals(movieDetails.getType())) {
            LOG.warn("Series '{}' determines a movie and no series", series.getTitle());
        }

        // movie details XML is still needed for some parts
        final String xml = imdbApiWrapper.getMovieDetailsXML(imdbId, throwTempError);
        // get header tag
        final String headerXml = HTMLTools.extractTag(xml, "<h1 class=\"header\">", "</h1>");

        // store for later use in season
        final String title = movieDetails.getTitle(); 
        final String originalTitle = parseOriginalTitle(headerXml);
        final String plot = (movieDetails.getBestPlot() == null) ? null : MetadataTools.cleanPlot(movieDetails.getBestPlot().getSummary());
        final String outline = (movieDetails.getPlot() == null) ? null : MetadataTools.cleanPlot(movieDetails.getPlot().getOutline());

        series.setTitle(title);
        series.setOriginalTitle(originalTitle);
        series.setStartYear(movieDetails.getYear());
        series.setEndYear(NumberUtils.toInt(movieDetails.getYearEnd(), -1));
        series.setPlot(plot);
        series.setOutline(outline);
        series.setGenres(movieDetails.getGenres());
        series.setStudios(imdbApiWrapper.getProductionStudios(imdbId));
        series.setCountries(HTMLTools.extractTags(xml, "Country" + HTML_H4_END, HTML_DIV_END, "<a href=\"", HTML_A_END));

        // CERTIFICATIONS
        imdbApiWrapper.parseCertifications(series, locale, movieDetails);

        // RELEASE INFO
        parseReleasedTitles(series, imdbId, locale);

        // AWARDS
        if (configService.getBooleanProperty("imdb.tvshow.awards", false)) {
            imdbApiWrapper.parseAwards(series);
        }
        
        // scan seasons
        this.scanSeasons(series, imdbId, title, originalTitle, plot, outline, locale);

        return true;
    }

    private void scanSeasons(ISeries series, String imdbId, String title, String originalTitle, String plot, String outline, Locale locale) {
        for (ISeason season : series.getSeasons()) {

            // get the episodes
            Map<Integer, ImdbEpisodeDTO> episodes = getEpisodes(imdbId, season.getNumber(), locale);

            if (!season.isDone()) {
                // use values from series
                season.setTitle(title);
                season.setOriginalTitle(originalTitle);
                season.setPlot(plot);
                season.setOutline(outline);

                Date publicationYear = null;
                for (ImdbEpisodeDTO episode : episodes.values()) {
                    if (publicationYear == null) {
                        publicationYear = episode.getReleaseDate();
                    } else if (episode.getReleaseDate() != null && publicationYear.after(episode.getReleaseDate())) {
                        // previous episode
                        publicationYear = episode.getReleaseDate();
                    }
                }
                season.setYear(MetadataTools.extractYearAsInt(publicationYear));

                // mark season as done
                season.setDone();

                // scan episodes
                for (IEpisode episode : season.getEpisodes()) {
                    this.scanEpisode(episode, episodes, locale);
                }
            }
        }
    }

    private void scanEpisode(IEpisode episode , Map<Integer, ImdbEpisodeDTO> episodes, Locale locale) {
        if (episode.isDone()) {
            // episode already done
            return;
        }
        
        ImdbEpisodeDTO dto = episodes.get(Integer.valueOf(episode.getNumber()));
        if (dto == null) {
            // mark episode as not found
            episode.setNotFound();
            return;
        }

        // fill in data
        episode.addId(SOURCE_IMDB, dto.getImdbId());
        episode.setTitle(dto.getTitle());
        episode.setRelease(dto.getReleaseCountry(), dto.getReleaseDate());

        // get movie details from IMDB
        ImdbMovieDetails movieDetails = imdbApiWrapper.getMovieDetails(dto.getImdbId(), locale, false);
        if (movieDetails == null || StringUtils.isBlank(movieDetails.getImdbId())) {
            episode.setNotFound();
            return;
        }

        // fill more data
        episode.setTitle(movieDetails.getTitle());
        episode.setOriginalTitle(movieDetails.getTitle());
        episode.setTagline(movieDetails.getTagline());
        episode.setRating(MetadataTools.parseRating(movieDetails.getRating()));

        // RELEASE DATE
        if (MapUtils.isNotEmpty(movieDetails.getReleaseDate())) {
            final Date releaseDate = MetadataTools.parseToDate(movieDetails.getReleaseDate().get(LITERAL_NORMAL));
            episode.setRelease(releaseDate);
        }

        // PLOT
        if (movieDetails.getBestPlot() != null) {
            episode.setPlot(MetadataTools.cleanPlot(movieDetails.getBestPlot().getSummary()));
        }

        // OUTLINE
        if (movieDetails.getPlot() != null) {
            episode.setOutline(MetadataTools.cleanPlot(movieDetails.getPlot().getOutline()));
        }

        // QUOTE
        if (movieDetails.getQuote() != null && CollectionUtils.isNotEmpty(movieDetails.getQuote().getLines())) {
            episode.setQuote(MetadataTools.cleanPlot(movieDetails.getQuote().getLines().get(0).getQuote()));
        }

        // CAST/CREW
        parseCastCrew(episode, dto.getImdbId());
    }

    private Map<Integer, ImdbEpisodeDTO> getEpisodes(String imdbId, int season, Locale locale) {
        Map<Integer, ImdbEpisodeDTO> episodes = new HashMap<>();
        
        List<ImdbEpisodeDTO> episodeList = imdbApiWrapper.getTitleEpisodes(imdbId, locale).get(Integer.valueOf(season));
        if (episodeList != null) {
            for (ImdbEpisodeDTO episode : episodeList) {
                episodes.put(Integer.valueOf(episode.getEpisode()), episode);
            }
        }
        return episodes;
    }
}
