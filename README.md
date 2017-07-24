## Tier Time Monitor Plugin

![images/icon.png](images/icon.png) 
<br />
The Tier Performance Metrics Monitor plugin queries a built-in Transaction Flow Dashboard to return response time, execution time, execution CPU time, transaction count, and transaction count rate. <br />
Metrics can be split by agent name, agent group, or technology. <br />
Metrics can also be filtered by agent group, agent name, and business transaction. <br />
The plugin supports both HTTP and HTTPS.<br />
<br />
<br />
<b>Plugin Parameters:</b><br />
	Protocol (required) - http protocol to access the dynaTrace server rest interface<br />
	Port  (required) - http port to access the dynaTrace server rest interface<br />
	username (required) - username to log into the dynaTrace server<br />
	password (required) - password to log into the dynaTrace server<br />
	Aggregation (required) - min/avg/max aggregation of measures<br />
	Split by (required) - split tier time by agent name, agent group, or technology<br />
	System Profile Filter (required) - System Profile Name<br />
	Timeframe Filter (required) - Timeframe for tier times<br />
	Additional Filters? (optional) - yes/no<br />
	Filter by Agent Group / Pattern (optional) - yes/no<br />
	Agent Filter Type (optional) - Filter by agent group or agent pattern<br />
	Agent Group Filter (optional) - Agent group name<br />
	Agent Pattern Match Type (optional) - agent pattern filter (starts, ends, or match)<br />
	Agent Pattern Filter (optional) - Pattern on which to filter the agents<br />
	Filter by Business Transaction (optional) - yes/no<br />
	Business Transaction Filter (optional) - Business Transaction name<br />
<br />
<br />
<b>Measures:</b><br />
	Response Time<br />
	Execution Time<br />
	Execution CPU Time<br />
	Transaction Count<br />
	Transaction Count Rate<br />
	Optional Measure Parameters:<br />
		Tier Filter (required) - Tier name (none = all tiers)<br />
<br />
Find further information in the [Dynatrace community](https://community.dynatrace.com/community/display/DL/Tier+Time+Monitor+Fastpack)