import sqlite3
import sys

con = sqlite3.connect(sys.argv[1])
con.row_factory = sqlite3.Row
for r in con.execute(
    "SELECT n._id, n.title, n.source, s.name AS sname, s.lang "
    "FROM novels n LEFT JOIN novelsources s ON s._id = n.source "
    "WHERE n.favorite = 1 ORDER BY n.title"
):
    print(f"  {r['_id']:>5}  src={r['source']} ({r['sname']}/{r['lang']})  {r['title']}")
