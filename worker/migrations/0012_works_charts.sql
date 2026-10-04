-- Platform chart rankings — a chart source's own ordering kept apart
-- from works_cache.popularity (the MangaBaka aggregate rank) so browse
-- can serve "the platform chart" without mislabelling aggregate data as
-- platform truth. Charts replace atomically on each refresh.
CREATE TABLE IF NOT EXISTS works_charts (
    chart TEXT NOT NULL,             -- e.g. 'webtoon_popular'
    miko_work_id TEXT NOT NULL,
    rank INTEGER NOT NULL,           -- 1 = chart top
    metric REAL,                     -- the chart's own score (likeitCount)
    fetched_at INTEGER NOT NULL,
    PRIMARY KEY (chart, miko_work_id)
);
CREATE INDEX IF NOT EXISTS works_charts_rank ON works_charts(chart, rank);
