# dslink-java-v2-buildingos

* Java - version 1.8 and up.
* [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0)

## Overview

A DSLink for more advanced and specifically tailored integration between DSA and the BuildingOS API than is currently available with the REST adapter.

## DSLink Structure

#### Root Node
- **Actions**:
	- **Set Credentials**: Takes in information necessary to authenticate user (API keys etc) and saves them, to be used for any requests to the BOS API.
	- **Add Organization**: Allows the user to choose from a drop-down of organizations they have access to, and creates a new child node to represent the chosen organization.
- **Metrics**: None
- **Child Nodes**: Any organization nodes that were added by the user with the `Add Organization` action

#### Organization Node
- **Actions**:
	- **Add Building**: Allows the user to choose from a drop-down of buildings that exist in the organization, and creates a new child node to represent the chosen building.
	- **Create Building**: Takes in the information needed to define a new building, and uses a POST request to create a new building in this organization in BuildingOS. Also creates a new child node to represent the new building.
- **Metrics**: Relevant metadata about the organization
- **Child Nodes**: Any building nodes that were added by the user with the Add/Create Building actions

#### Building Node
- **Actions**:
	- **Add Meter**: Allows the user to choose from a drop-down of meters that exist in the building, and creates a new child node to represent the chosen meter. 
Also allows the user to optionally input a DSA path to subscribe to. As in the REST adapter DSLink, values from this path will be sent to the chosen meter in BuildingOS
	- **Bulk Add Meters**: A convenience for adding multiple meters by inputting a table
	- **Create Meter**: Takes in the information needed to define a new meter, and uses a POST request to create a new meter in this building in BuildingOS. Also creates a new child node to represent the new meter.
- **Metrics**: Relevant metadata about the building, e.g. id, address, building type
- **Child Nodes**: Any meter nodes that were added by the user with the Add/Create Meter actions

#### Meter Node
- **Actions**: 
	- **Get Data**: Returns a segment of this meter’s historical data, for the time range and resolution specified by the user
	- **Bulk Insert Records**: Push multiple historical records to the meter. Takes in an array of records, each of which should be an array of form `[timestamp, value, status]`. Some notes:
		- The status is optional, and defaults to "OK"
		- Records can also be of form `[index, timestamp, value, status]`, in which case the index will be ignored. This is for better interoperability with DSA tables.
		- The table output by the GetHistory action of a historian DSLink (such as ETSDB or DynamoDB) can be used as input to this action.
- **Metrics**: 
	- Relevant metadata about the meter, e.g. status, units, resource type
	- The DSA path from which to push data to this meter
- **Child Nodes**: None


## Additional Notes
    
- Meters in this DSLink are similar to Rules in the REST adapter, in that they specify a source DSA path to subscribe to and a destination BOS meter to push to, and ensure that data gets pushed from the source to the destination. There are, however, some important differences:
  - There is no need to specify the format of the POST requests used, as this is known in advance.
  - The REST adapter would send an update as soon as the source value changed, and only stored readings in an etsdb database if the server was unavailable. In contrast, the BuildingOS DSLink will store all updates in an etsdb database by default, and push them to BOS on a user-defined interval.
