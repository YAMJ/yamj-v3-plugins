/*
 *      Copyright (c) 2004-2015 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
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
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.plugin.rottentomatoes;

import com.omertron.rottentomatoesapi.RottenTomatoesException;
import com.omertron.rottentomatoesapi.model.RTMovie;
import java.util.List;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamj.plugin.api.NeedsConfigService;
import org.yamj.plugin.api.extras.MovieExtrasScanner;
import org.yamj.plugin.api.model.IMovie;
import org.yamj.plugin.api.service.PluginConfigService;
import ro.fortsoft.pf4j.Extension;

@Extension
public class RottenTomatoesRatingScanner implements MovieExtrasScanner, NeedsConfigService {

    private static final String SCANNER_NAME = "rottentomatoes";
    private static final Logger LOG = LoggerFactory.getLogger(RottenTomatoesRatingScanner.class);

    private PluginConfigService configService;
    
    @Override
    public String getScannerName() {
        return SCANNER_NAME;
    }

    @Override
    public void setConfigService(PluginConfigService configService) {
        this.configService = configService;
    }

    @Override
    public boolean isEnabled() {
        return configService.getBooleanProperty("rottentomatoes.rating.enabled", false);
    }

    @Override
    public void scanExtras(IMovie movie) {
        RTMovie rtMovie = null;
        int rtId = NumberUtils.toInt(movie.getId(SCANNER_NAME));

        if (rtId > 0) {
            try {
                rtMovie = RottenTomatoesPlugin.getRottenTomatoesApi().getDetailedInfo(rtId);
            } catch (RottenTomatoesException ex) { //NOSONAR
                LOG.warn("Failed to get RottenTomatoes information: {}", ex.getMessage());
            }
        } else {
            try {
                List<RTMovie> rtMovies = RottenTomatoesPlugin.getRottenTomatoesApi().getMoviesSearch(movie.getTitle());
                for (RTMovie tmpMovie : rtMovies) {
                    if (movie.getTitle().equalsIgnoreCase(tmpMovie.getTitle()) && (movie.getYear() == tmpMovie.getYear())) {
                        rtId = tmpMovie.getId();
                        rtMovie = tmpMovie;
                        movie.addId(SCANNER_NAME, String.valueOf(rtId));
                        break;
                    }
                }
            } catch (RottenTomatoesException ex) { //NOSONAR
                LOG.warn("Failed to get RottenTomatoes information: {}", ex.getMessage());
            }
        }

        if (rtMovie != null) {
            for (String type : configService.getPropertyAsList("rottentomatoes.rating.priority", "critics_score,audience_score,critics_rating,audience_rating")) {
                int rating = NumberUtils.toInt(rtMovie.getRatings().get(type));
                if (rating > 0) {
                    LOG.debug("{} - {} found: {}", movie.getTitle(), type, rating);
                    movie.setRating(rating);
                    return;
                }
            }
        }
        
        LOG.debug("No RottenTomatoes rating found for '{}'", movie.getTitle());
    }
}
